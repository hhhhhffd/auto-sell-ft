package com.sellhelper.hud;

import com.sellhelper.logic.SellHelperLogic;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public class SellHelperHud {

    public static void register(SellHelperLogic logic) {
        HudLayerRegistrationCallback.EVENT.register(layeredDrawer ->
                layeredDrawer.attachLayerAfter(
                        IdentifiedLayer.CROSSHAIR,
                        Identifier.of("sellhelper", "overlay"),
                        (drawContext, tickCounter) -> {
                            if (!logic.isActive()) return;

                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.getWindow() == null) return;

                            int screenWidth  = client.getWindow().getScaledWidth();
                            int screenHeight = client.getWindow().getScaledHeight();

                            // 100 px right of crosshair centre, vertically centred
                            int x = screenWidth  / 2 + 100;
                            int y = screenHeight / 2 - 4;

                            drawContext.drawTextWithShadow(
                                    client.textRenderer,
                                    "\u2714 SellHelper Active",
                                    x, y,
                                    0x55FF55  // bright green
                            );
                        }
                )
        );
    }
}

