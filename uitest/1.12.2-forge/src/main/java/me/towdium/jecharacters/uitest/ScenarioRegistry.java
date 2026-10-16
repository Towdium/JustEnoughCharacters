package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.api.ClientScenario;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import me.towdium.jecharacters.uitest.api.ScenarioOptions;
import me.towdium.jecharacters.uitest.datadriven.DataDrivenScenario;
import me.towdium.jecharacters.uitest.datadriven.JsonScenario;
import me.towdium.jecharacters.uitest.datadriven.JsonScenarioSources;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ScenarioRegistry {

    private static final Logger LOG = LogManager.getLogger("jechtest-uitest");

    private static volatile ASMDataTable table;

    private static volatile List<String> registrationProblems = Collections.emptyList();

    private ScenarioRegistry() {}

    public static void setTable(ASMDataTable value) {
        table = value;
    }

    public static final class Entry {

        private final String name;
        private final String group;
        private final List<String> tags;
        private final boolean requiresWorld;
        private final Class<?> type;
        private final ScenarioOptions options;
        private final JsonScenario json;
        private final String problem;

        Entry(
                String name,
                String group,
                List<String> tags,
                boolean requiresWorld,
                Class<?> type,
                ScenarioOptions options,
                JsonScenario json,
                String problem) {
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

        public String source() {
            return json != null ? json.source() : name;
        }

        public Consumer<ScenarioBuilder> define() {
            if (problem != null) {
                final String problemSource = source();
                final String problemText = problem;
                final String problemName = name;
                return new Consumer<ScenarioBuilder>() {
                    @Override
                    public void accept(ScenarioBuilder builder) {
                        DataDrivenScenario.defineFailure(problemName, problemSource, problemText, builder);
                    }
                };
            }
            if (json != null) {
                final JsonScenario parsed = json;
                return new Consumer<ScenarioBuilder>() {
                    @Override
                    public void accept(ScenarioBuilder builder) {
                        DataDrivenScenario.define(parsed, builder);
                    }
                };
            }
            Method method;
            try {
                method = type.getMethod("define", ScenarioBuilder.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "scenario '" + name + "' (" + type.getName()
                                + ") has no public void define(ScenarioBuilder)",
                        e);
            }
            final Method target = method;
            final Class<?> owner = type;
            final String ownerName = name;
            return new Consumer<ScenarioBuilder>() {
                @Override
                public void accept(ScenarioBuilder builder) {
                    Object instance;
                    try {
                        instance = owner.newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(
                                "scenario '" + ownerName + "' (" + owner.getName()
                                        + ") needs a public no-argument constructor",
                                e);
                    }
                    try {
                        target.invoke(instance, builder);
                    } catch (ReflectiveOperationException e) {
                        Throwable cause = e.getCause() == null ? e : e.getCause();
                        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                        throw new IllegalStateException(
                                "scenario '" + ownerName + "' failed while defining its steps", cause);
                    }
                }
            };
        }
    }

    public static List<Entry> scan() {
        ASMDataTable current = table;
        Map<String, Entry> byName = new LinkedHashMap<String, Entry>();
        if (current == null) {
            LOG.warn("jechtest: scenario registry has no FML annotation table; no annotated scenario can be found");
        } else {
            for (ASMDataTable.ASMData data : current.getAll(ClientScenario.class.getName())) {
                Entry entry = read(data.getClassName());
                if (entry == null) continue;
                keep(byName, entry);
            }
        }
        List<String> problems = new ArrayList<String>();
        for (JsonScenarioSources.Loaded loaded : JsonScenarioSources.loadAll()) {
            Entry entry = fromJson(loaded);
            if (entry.problem != null) {
                problems.add(entry.name + ": " + entry.problem);
            }
            keep(byName, entry);
        }
        if (!problems.isEmpty()) {
            LOG.error("{} JSON scenario file(s) could not be read and are registered as failures: {}",
                    problems.size(), String.join(" | ", problems));
        }
        registrationProblems = Collections.unmodifiableList(problems);
        List<Entry> entries = new ArrayList<Entry>(byName.values());
        entries.sort(Comparator.comparing(Entry::name));
        return entries;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<String>();
        for (Entry entry : scan()) names.add(entry.name());
        return names;
    }

    public static List<String> registrationProblems() {
        return registrationProblems;
    }

    private static void keep(Map<String, Entry> byName, Entry entry) {
        Entry previous = byName.get(entry.name());
        if (previous != null) {
            LOG.warn("jechtest: scenario name '{}' is declared by both {} and {}; keeping the first",
                    entry.name(), previous.source(), entry.source());
            return;
        }
        byName.put(entry.name(), entry);
    }

    private static Entry fromJson(JsonScenarioSources.Loaded loaded) {
        if (loaded.failed()) {
            return new Entry(
                    loaded.name(),
                    "",
                    Collections.<String>emptyList(),
                    true,
                    DataDrivenScenario.class,
                    ScenarioOptions.defaults(),
                    null,
                    loaded.failure());
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
                null);
    }

    private static Entry read(String className) {
        Class<?> type;
        try {
            type = load(className);
        } catch (ClassNotFoundException e) {
            LOG.warn("jechtest: annotated scenario class {} is not on the classpath any more", className, e);
            return null;
        } catch (NoClassDefFoundError e) {
            LOG.warn("jechtest: annotated scenario class {} cannot be linked (missing dependency)", className, e);
            return null;
        }
        ClientScenario annotation = type.getAnnotation(ClientScenario.class);
        if (annotation == null) return null;
        return new Entry(
                annotation.name(),
                annotation.group(),
                Collections.unmodifiableList(Arrays.asList(annotation.tags())),
                annotation.requiresWorld(),
                type,
                optionsOf(annotation),
                null,
                null);
    }

    private static ScenarioOptions optionsOf(ClientScenario annotation) {
        ScenarioOptions options = ScenarioOptions.defaults();
        if (annotation.timeoutMs() > 0) options = options.withDefaultTimeoutMs(annotation.timeoutMs());
        if (annotation.scenarioTimeoutMs() > 0) options = options.withScenarioTimeoutMs(annotation.scenarioTimeoutMs());
        return options;
    }

    private static Class<?> load(String className) throws ClassNotFoundException {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(className, false, context);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(className, false, ScenarioRegistry.class.getClassLoader());
    }
}
