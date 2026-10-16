package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.MouseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHelper.class)
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

    @Invoker("onPress")
    void invokeOnPress(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    void invokeOnScroll(long window, double xOffset, double yOffset);
}
