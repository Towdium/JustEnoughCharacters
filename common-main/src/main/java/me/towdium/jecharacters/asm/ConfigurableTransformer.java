package me.towdium.jecharacters.asm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ConfigurableTransformer implements ITransformer {

    protected final Map<String, Set<TransformTarget>> targets = new HashMap<>();

    @Override
    public void init(JsonObject config, Set<String> removal) {
        if (!config.has(getConfigOwner())) return;
        JsonObject configObj = config.get(getConfigOwner()).getAsJsonObject();
        for (JsonElement target : configObj.get("default").getAsJsonArray()) {
            String s = target.getAsString();
            if (removal.contains(s)) continue;
            TransformTarget tt = TransformTarget.of(target.getAsString());
            targets.computeIfAbsent(tt.getOwner(), k -> new HashSet<>()).add(tt);
        }
        for (JsonElement target : configObj.get("additional").getAsJsonArray()) {
            String s = target.getAsString();
            if (removal.contains(s)) continue;
            TransformTarget tt = TransformTarget.of(target.getAsString());
            targets.computeIfAbsent(tt.getOwner(), k -> new HashSet<>()).add(tt);
        }
    }

    @Override
    public Set<String> targetClasses() {
        return Collections.unmodifiableSet(targets.keySet());
    }

    @Override
    public boolean accept(String className) {
        return targets.containsKey(className);
    }

    @Override
    public ClassNode transform(ClassNode node) {
        Set<TransformTarget> targetMethods = targets.get(node.name);
        if (targetMethods == null) return node;

        for (MethodNode method : node.methods) {
            for (TransformTarget target : targetMethods) {
                if (target.matches(node.name, method)) {
                    this.transformMethod(method);
                }
            }
        }

        return node;
    }

    protected abstract String getConfigOwner();

    protected abstract void transformMethod(MethodNode method);

}
