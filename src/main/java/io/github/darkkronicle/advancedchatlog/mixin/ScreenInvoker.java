package io.github.darkkronicle.advancedchatlog.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenInvoker {

    @Invoker("handleClickEvent")
    static void advancedchatlog$handleClickEvent(
            ClickEvent clickEvent, MinecraftClient client, Screen screen) {
        throw new AssertionError();
    }
}