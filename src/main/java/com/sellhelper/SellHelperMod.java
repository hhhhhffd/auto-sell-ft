package com.sellhelper;

import com.sellhelper.command.SellHelperCommand;
import com.sellhelper.hud.SellHelperHud;
import com.sellhelper.keybind.SellHelperKeybind;
import com.sellhelper.logic.SellHelperLogic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public class SellHelperMod implements ClientModInitializer {

    public static final String MOD_ID = "sellhelper";

    private static SellHelperLogic logic;

    @Override
    public void onInitializeClient() {
        logic = new SellHelperLogic();

        SellHelperKeybind.register();
        SellHelperCommand.register();
        SellHelperHud.register(logic);

        // Check keybind every tick (main thread)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (SellHelperKeybind.TOGGLE.wasPressed()) {
                logic.toggle();
            }
        });

        // Purchase detection via chat
        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, params, receptionTimestamp) ->
                        logic.onChatMessage(message.getString())
        );
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) logic.onChatMessage(message.getString());
        });
    }

    public static SellHelperLogic getLogic() {
        return logic;
    }
}
