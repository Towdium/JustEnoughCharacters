package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.api.ClientScenario;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import me.towdium.jecharacters.uitest.api.ScenarioOptions;
import me.towdium.jecharacters.uitest.api.Session;
import me.towdium.jecharacters.uitest.datadriven.DataDrivenScenario;
import me.towdium.jecharacters.uitest.datadriven.JsonScenario;
import me.towdium.jecharacters.uitest.datadriven.JsonScenarioSources;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every {@link ClientScenario} the loaded mods declare, found by scanning their class files and read
 * back through reflection, plus every scenario declared as JSON.
 *
 * <p>Discovery uses {@code ModList#getAllScanData} so a downstream mod needs no registration call,
 * but the annotation is read from the loaded class rather than from the scan's raw values: a class
 * file only records the members an annotation actually sets, so defaults would read as absent.
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
        Type annotationType = Type.getType(ClientScenario.class);
        Map<String, Entry> byName = new LinkedHashMap<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : scanData.getAnnotations()) {
                if (!annotation.annotationType().equals(annotationType)) continue;
                Entry entry = read(annotation.memberName());
                if (entry == null) continue;
                keep(byName, entry);
            }
        }
        List<String> problems = new ArrayList<>();
        for (JsonScenarioSources.Loaded loaded : JsonScenarioSources.loadAll()) {
            Entry entry = fromJson(loaded);
            if (entry.problem() != null) {
                problems.add(entry.name() + ": " + entry.problem());
            }
            keep(byName, entry);
        }
        if (!problems.isEmpty()) {
            log.error(
                "{} JSON scenario file(s) could not be read and are registered as failures: {}",
                problems.size(),
                String.join(" | ", problems)
            );
        }
        registrationProblems = List.copyOf(problems);
        List<Entry> entries = new ArrayList<>(byName.values());
        entries.sort(Comparator.comparing(Entry::name));
        return entries;
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

    private static @Nullable Entry read(String className) {
        Class<?> type;
        try {
            type = load(className);
        } catch (ClassNotFoundException e) {
            log.warn("annotated scenario class {} is not on the classpath any more", className, e);
            return null;
        }
        ClientScenario annotation = type.getAnnotation(ClientScenario.class);
        if (annotation == null) return null;
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

    private static Class<?> load(String className) throws ClassNotFoundException {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(className, false, context);
            } catch (ClassNotFoundException ignored) {
                // fall through to the harness's own loader, which is the mod's loader in dev runs
            }
        }
        return Class.forName(className, false, ScenarioRegistry.class.getClassLoader());
    }
}
