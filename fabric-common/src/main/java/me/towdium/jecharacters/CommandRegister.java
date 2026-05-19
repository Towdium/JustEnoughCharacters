package me.towdium.jecharacters;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public interface CommandRegister {

    void register(LiteralArgumentBuilder<?> builder);

}
