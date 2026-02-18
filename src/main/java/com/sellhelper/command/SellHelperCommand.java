package com.sellhelper.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.sellhelper.config.SellHelperConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public class SellHelperCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("sellhelper")
                                .then(ClientCommandManager.literal("set_item")
                                        .executes(ctx -> setItem(ctx.getSource(), -1))
                                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setItem(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                ))
                                        )
                                )
                                .then(ClientCommandManager.literal("set_price")
                                        .then(ClientCommandManager.argument("price", LongArgumentType.longArg(0))
                                                .executes(ctx -> setPrice(
                                                        ctx.getSource(),
                                                        LongArgumentType.getLong(ctx, "price")
                                                ))
                                        )
                                )
                )
        );
    }

    private static int setItem(FabricClientCommandSource source, int amount) {
        ItemStack held = source.getPlayer().getMainHandStack();
        if (held.isEmpty()) {
            source.sendError(Text.literal("Держите предмет в руке!"));
            return 0;
        }

        SellHelperConfig config = SellHelperConfig.get();
        config.itemId = Registries.ITEM.getId(held.getItem()).toString();
        config.amount = (amount == -1) ? held.getCount() : amount;
        config.save();

        source.sendFeedback(Text.literal(
                "[SellHelper] Предмет: " + config.itemId + " x" + config.amount
        ));
        return 1;
    }

    private static int setPrice(FabricClientCommandSource source, long price) {
        SellHelperConfig config = SellHelperConfig.get();
        config.price = price;
        config.save();

        source.sendFeedback(Text.literal("[SellHelper] Цена: " + price));
        return 1;
    }
}
