package me.towdium.jecharacters.uitest.platform;

public interface ServerPlatform {

    Object overworld(Object server);

    Object localPlayer(Object server);

    String shortPos(Object pos);

    void setBlock(Object level, Object pos, Object state);

    void fill(Object level, Object from, Object to, Object state);

    Object airState();

    Object blockEntity(Object level, Object pos);

    void acceptBlockEntity(Object action, Object blockEntity);

    void teleport(Object player, Object level, double x, double y, double z);

    void look(Object player, Object level, float yaw, float pitch);

    Object itemByName(String id);

    void addToInventory(Object player, Object item, int count);

    String canonicalItemId(String id);

    Object commandSource(Object server);

    Object worldRuleSource(Object server, Object level);

    void perform(Object server, Object source, String command);

    String dimensionId(Object level);

    Iterable<Object> nonPlayerEntities(Object server);

    void discard(Object entity);

    static void register(ServerPlatform platform) {
        Holder.platform = platform;
    }

    static ServerPlatform current() {
        return Holder.platform;
    }

    final class Holder {

        static ServerPlatform platform;
    }
}
