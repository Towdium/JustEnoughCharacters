package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.api.ClientScenario;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import me.towdium.jecharacters.uitest.api.ScenarioOptions;
import me.towdium.jecharacters.uitest.api.Session;
import me.towdium.jecharacters.uitest.datadriven.DataDrivenScenario;
import me.towdium.jecharacters.uitest.datadriven.JsonScenario;
import me.towdium.jecharacters.uitest.datadriven.JsonScenarioSources;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every {@link ClientScenario} the loaded mods declare, plus every scenario declared as JSON.
 *
 * <p>Discovery goes through Fabric Loader's entrypoints: a mod declares its scenario classes under
 * the {@value #scenarioEntrypoint} namespace in its {@code fabric.mod.json}, which is the
 * fabric-native counterpart of FML's class scan — declaration next to the mod, no registration call
 * at runtime. The annotation is still read from the loaded class rather than from the entrypoint
 * declaration: the class's own {@code @ClientScenario} is what names, tags and tunes a scenario, and
 * a class declared under the namespace without it is reported as a problem rather than silently
 * dropped.
 *
 * <p><b>Port note (NeoForge → Fabric).</b> The NeoForge copy of this class walks
 * {@code ModList#getAllScanData}, FML's index of every {@code @ClientScenario} in every loaded mod.
 * Fabric Loader has no such index, so the same set comes from an explicit entrypoint namespace; the
 * annotation is read the same way in both copies, and the JSON half is untouched.
 *
 * <p>The JSON half comes from {@link JsonScenarioSources}: the {@code uitest/scenarios/*.json}
 * resources of every loaded mod, plus the {@code <game dir>/jech-scenarios} folder, which is re-read
 * on every scan so a scenario can be authored without rebuilding. A JSON file that cannot be parsed
 * is <em>registered</em> — as a scenario that fails when it runs, naming its file and its problem —
 * because a file that is present and unreadable must not be indistinguishable from a file that is
 * not there.
 *
 * <p>Not thread-safe and not meant to be: the engine calls {@link #scan()} on the client thread,
 * before the first scenario starts.
 */
public final class ScenarioRegistry {

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    /**
     * The Fabric Loader entrypoint namespace a mod declares its Java scenarios under: a
     * {@code "jechtest:scenarios"} list of class names in {@code fabric.mod.json}. This is the
     * fabric-native counterpart of the NeoForge copy's {@code @ClientScenario} class scan.
     */
    public static final String scenarioEntrypoint = "jechtest:scenarios";

    /** What the last scan made of the JSON files that could not be read. */
    private static volatile List<String> registrationProblems = List.of();

    private ScenarioRegistry() {}

    /**
     * One registered scenario.
     *
     * @param name          the unique name the selection grammar matches
     * @param group         the annotation's group label, empty when unset
     * @param tags          the annotation's tags
     * @param requiresWorld whether the scenario needs a loaded world
     * @param type          the annotated class, or {@link DataDrivenScenario} for a JSON scenario
     * @param options       the per-scenario tuning
     * @param json          the parsed JSON scenario, or {@code null} for an annotated one
     * @param problem       why a JSON file could not be read, or {@code null} when it could
     */
    public record Entry(
        String name,
        String group,
        List<String> tags,
        boolean requiresWorld,
        Class<?> type,
        ScenarioOptions options,
        @Nullable JsonScenario json,
        @Nullable String problem
    ) {

        /** Whether the annotation's type is one of the scenario's types or an enclosing one. */
        public boolean declaredBy(String modId) {
            return type.getName().contains("." + modId + ".");
        }

        /**
         * Runs the scenario against a fresh builder.
         *
         * <p>A JSON scenario is interpreted; an annotated one is instantiated. A JSON file that could
         * not be read registers one step that fails with the reason, so its problem reaches the
         * report attached to a name.
         *
         * @throws IllegalStateException when an annotated class cannot be instantiated, does not
         *                               declare {@code public void define(ScenarioBuilder)}, or
         *                               throws while defining its steps
         */
        public void define(ScenarioBuilder builder) {
            if (problem != null) {
                DataDrivenScenario.defineFailure(name, jsonSource(), problem, builder);
                return;
            }
            if (json != null) {
                DataDrivenScenario.define(json, builder);
                return;
            }
            try {
                Object instance = type.getDeclaredConstructor().newInstance();
                type.getMethod("define", ScenarioBuilder.class).invoke(instance, builder);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw new IllegalStateException(
                    "scenario " + name + " threw while defining its steps",
                    cause == null ? e : cause
                );
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                    "scenario " + name + " needs a public no-argument constructor and "
                            + "public void define(ScenarioBuilder)",
                    e
                );
            }
        }

        /** Where the scenario came from, for messages. */
        public String jsonSource() {
            return json != null ? json.source() : name;
        }
    }

    /** Scans every loaded mod for annotated and JSON scenarios, sorted by name and deduplicated. */
    public static List<Entry> scan() {
        Map<String, Entry> byName = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (EntrypointContainer<Object> container : FabricLoader.getInstance()
                .getEntrypointContainers(scenarioEntrypoint, Object.class)) {
            String modId = container.getProvider().getMetadata().getId();
            Object instance;
            try {
                instance = container.getEntrypoint();
            } catch (Throwable thrown) {
                problems.add(modId + ": its " + scenarioEntrypoint + " entrypoint could not be "
                        + "constructed (" + describe(thrown) + ")");
                continue;
            }
            ClientScenario annotation = instance.getClass().getAnnotation(ClientScenario.class);
            if (annotation == null) {
                problems.add(instance.getClass().getName() + " (mod " + modId + "): declared under "
                        + scenarioEntrypoint + " without @ClientScenario, so it names nothing");
                continue;
            }
            keep(byName, fromAnnotation(annotation, instance.getClass()));
        }
        for (JsonScenarioSources.Loaded loaded : JsonScenarioSources.loadAll()) {
            Entry entry = fromJson(loaded);
            if (entry.problem() != null) {
                problems.add(entry.name() + ": " + entry.problem());
            }
            keep(byName, entry);
        }
        if (!problems.isEmpty()) {
            log.error(
                "{} scenario declaration(s) could not be registered and are reported as failures: {}",
                problems.size(),
                String.join(" | ", problems)
            );
        }
        registrationProblems = List.copyOf(problems);
        List<Entry> entries = new ArrayList<>(byName.values());
        entries.sort(Comparator.comparing(Entry::name));
        return entries;
    }

    private static String describe(Throwable thrown) {
        Throwable cause = thrown.getCause() == null ? thrown : thrown.getCause();
        return cause.getClass().getName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    /**
     * The JSON files the last {@link #scan()} could not read, as {@code name: reason} lines.
     *
     * <p>It is read back by the runner so a run's log names them even when the selection does not
     * include them: a broken file is a fact about the checkout, not about one scenario.
     */
    public static List<String> registrationProblems() {
        return registrationProblems;
    }

    /** The registered scenario names, sorted. */
    public static List<String> names() {
        return scan().stream().map(Entry::name).toList();
    }

    private static void keep(Map<String, Entry> byName, Entry entry) {
        Entry previous = byName.putIfAbsent(entry.name(), entry);
        if (previous != null) {
            log.warn(
                "scenario name '{}' is declared by both {} and {}; keeping the first",
                entry.name(),
                previous.jsonSource(),
                entry.jsonSource()
            );
        }
    }

    private static Entry fromJson(JsonScenarioSources.Loaded loaded) {
        if (loaded.failed()) {
            return new Entry(
                loaded.name(),
                "",
                List.of(),
                true,
                DataDrivenScenario.class,
                ScenarioOptions.defaults(),
                null,
                loaded.failure()
            );
        }
        JsonScenario json = loaded.scenario();
        return new Entry(
            json.name(),
            json.group(),
            json.tags(),
            json.requiresWorld(),
            DataDrivenScenario.class,
            json.options(),
            json,
            null
        );
    }

    private static Entry fromAnnotation(ClientScenario annotation, Class<?> type) {
        return new Entry(
            annotation.name(),
            annotation.group(),
            List.of(annotation.tags()),
            annotation.requiresWorld(),
            type,
            optionsOf(annotation),
            null,
            null
        );
    }

    private static ScenarioOptions optionsOf(ClientScenario annotation) {
        ScenarioOptions options = ScenarioOptions.defaults();
        if (annotation.guiScale() > 0) options = options.withGuiScale(annotation.guiScale());
        options = options.withHideGui(annotation.hideGui());
        if (annotation.settleMs() > 0) options = options.withSettleMs(annotation.settleMs());
        if (annotation.timeoutMs() > 0) options = options.withDefaultTimeoutMs(annotation.timeoutMs());
        if (annotation.scenarioTimeoutMs() > 0) options = options.withScenarioTimeoutMs(annotation.scenarioTimeoutMs());
        if (annotation.session() != Session.shared) options = options.withSession(annotation.session());
        return options;
    }
}
