package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("mouseGrabbed")
    void setMouseGrabbed(boolean grabbed);

    @Accessor("accumulatedDX")
    double getAccumulatedDx();

    @Accessor("accumulatedDX")
    void setAccumulatedDx(double delta);

    @Accessor("accumulatedDY")
    double getAccumulatedDy();

    @Accessor("accumulatedDY")
    void setAccumulatedDy(double delta);

    @Invoker("onButton")
    void invokeOnButton(long window, MouseButtonInfo buttonInfo, int action);

    @Invoker("onScroll")
    void invokeOnScroll(long window, double xOffset, double yOffset);
}
