package com.sellhelper.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SellHelperKeybind {

    public static KeyBinding TOGGLE;

    public static void register() {
        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sellhelper.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.sellhelper"
        ));
    }
}
