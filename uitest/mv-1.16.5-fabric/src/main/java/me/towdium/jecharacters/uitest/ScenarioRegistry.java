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
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;

public final class ScenarioRegistry {

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    public static final String scenarioEntrypoint = "jechtest:scenarios";

    private static volatile List<String> registrationProblems = Collections.emptyList();

    private ScenarioRegistry() {}

    public static final class Entry {

        private final String name;
        private final String group;
        private final List<String> tags;
        private final boolean requiresWorld;
        private final Class<?> type;
        private final ScenarioOptions options;
        @Nullable
        private final JsonScenario json;
        @Nullable
        private final String problem;

        public Entry(
            String name,
            String group,
            List<String> tags,
            boolean requiresWorld,
            Class<?> type,
            ScenarioOptions options,
            @Nullable JsonScenario json,
            @Nullable String problem) {
            this.name = name;
            this.group = group;
            this.tags = tags;
            this.requiresWorld = requiresWorld;
            this.type = type;
            this.options = options;
            this.json = json;
            this.problem = problem;
        }

        public String name() {
            return name;
        }

        public String group() {
            return group;
        }

        public List<String> tags() {
            return tags;
        }

        public boolean requiresWorld() {
            return requiresWorld;
        }

        public Class<?> type() {
            return type;
        }

        public ScenarioOptions options() {
            return options;
        }

        @Nullable
        public JsonScenario json() {
            return json;
        }

        @Nullable
        public String problem() {
            return problem;
        }

        public boolean declaredBy(String modId) {
            return type.getName().contains("." + modId + ".");
        }

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

        public String jsonSource() {
            return json != null ? json.source() : name;
        }
    }

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
        registrationProblems = Collections.unmodifiableList(new ArrayList<>(problems));
        List<Entry> entries = new ArrayList<>(byName.values());
        entries.sort(Comparator.comparing(Entry::name));
        return entries;
    }

    private static String describe(Throwable thrown) {
        Throwable cause = thrown.getCause() == null ? thrown : thrown.getCause();
        return cause.getClass().getName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    public static List<String> registrationProblems() {
        return registrationProblems;
    }

    public static List<String> names() {
        return scan().stream().map(Entry::name).collect(Collectors.toList());
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
                Collections.emptyList(),
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
            Arrays.asList(annotation.tags()),
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
