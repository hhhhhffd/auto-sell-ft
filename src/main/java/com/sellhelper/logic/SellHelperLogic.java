package com.sellhelper.logic;

import com.sellhelper.config.SellHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core automation logic for SellHelper.
 *
 * All Minecraft API calls are dispatched to the main thread via client.execute().
 * Delays between actions are scheduled on a dedicated single-threaded executor
 * using ThreadLocalRandom values to mimic human timing.
 *
 * Slot index mapping inside PlayerScreenHandler (InventoryScreen):
 *   0        = crafting result
 *   1–4      = crafting grid
 *   5–8      = armour
 *   9–35     = main inventory rows
 *   36–44    = hotbar (PlayerInventory.main[0–8])
 *   45       = off-hand
 */
public class SellHelperLogic {

    // ------------------------------------------------------------------ state

    private final AtomicBoolean active       = new AtomicBoolean(false);
    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> reselTimer = null;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SellHelper-Scheduler");
                t.setDaemon(true);
                return t;
            });

    // ----------------------------------------------------------- public API

    public void toggle() {
        if (active.get()) deactivate();
        else              activate();
    }

    public boolean isActive() {
        return active.get();
    }

    /** Called by ClientReceiveMessageEvents when a chat line arrives. */
    public void onChatMessage(String message) {
        if (message.contains("У Вас купили") && message.contains("на /ah")) {
            stopReselTimer();
            if (active.get()) {
                // Force-reset so startCycle() can acquire the lock
                cycleRunning.set(false);
                scheduleAfter(this::startCycle, rnd(300, 600));
            }
        }
    }

    // --------------------------------------------------------- activation

    private void activate() {
        if (active.compareAndSet(false, true)) {
            startCycle();
        }
    }

    private void deactivate() {
        active.set(false);
        cycleRunning.set(false);
        stopReselTimer();
    }

    // ------------------------------------------------------ cycle entry point

    private void startCycle() {
        if (!active.get()) return;
        if (!cycleRunning.compareAndSet(false, true)) return;
        scheduler.execute(this::runCycle);
    }

    /**
     * Main cycle logic — always dispatched to the game's main thread so that
     * inventory reads are thread-safe.
     */
    private void runCycle() {
        if (!active.get()) { cycleRunning.set(false); return; }

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player == null || !active.get()) {
                cycleRunning.set(false);
                return;
            }

            SellHelperConfig cfg      = SellHelperConfig.get();
            PlayerInventory   inv     = client.player.getInventory();
            int               selSlot = inv.selectedSlot;

            // ── Step 1: main hand ──────────────────────────────────────────
            ItemStack mainHand = inv.getStack(selSlot);
            if (isTarget(mainHand, cfg) && mainHand.getCount() >= cfg.amount) {
                handleSplit(selSlot, mainHand.getCount(), cfg);
                return;
            }

            // ── Step 2: rest of hotbar ─────────────────────────────────────
            for (int i = 0; i < 9; i++) {
                if (i == selSlot) continue;
                ItemStack s = inv.getStack(i);
                if (isTarget(s, cfg) && s.getCount() >= cfg.amount) {
                    final int slot  = i;
                    final int count = s.getCount();
                    switchHotbarSlot(slot, () -> handleSplit(slot, count, cfg));
                    return;
                }
            }

            // ── Step 3: main inventory (slots 9–35) ────────────────────────
            for (int i = 9; i <= 35; i++) {
                ItemStack s = inv.getStack(i);
                if (isTarget(s, cfg) && s.getCount() >= cfg.amount) {
                    int freeHotbar = findFreeHotbarSlot(inv);
                    if (freeHotbar >= 0) {
                        final int invSlot = i;
                        moveInvToHotbar(invSlot, freeHotbar, () -> {
                            cycleRunning.set(false);
                            startCycle();
                        });
                    } else {
                        doFailback(cfg);
                    }
                    return;
                }
            }

            // ── No item found anywhere ─────────────────────────────────────
            doAllSold();
        });
    }

    // --------------------------------------------------- split + sell

    /**
     * If the hotbar slot has exactly cfg.amount items → sell immediately.
     * If it has more → open inventory, split, close, sell.
     */
    private void handleSplit(int hotbarSlot, int count, SellHelperConfig cfg) {
        if (count == cfg.amount) {
            scheduleAfter(() -> doSell(cfg), rnd(100, 200));
            return;
        }
        // count > cfg.amount
        openInventory(() -> splitStack(hotbarSlot, count, cfg.amount,
                () -> closeInventory(() -> doSell(cfg))));
    }

    /**
     * Performs the actual slot-click sequence to leave exactly {@code amount}
     * items in the hotbar slot and move the excess to an empty inventory slot.
     *
     * Algorithm (all clicks in one main-thread callback):
     *   1. Left-click hotbar slot  → cursor picks up all C items
     *   2. Right-click empty slot (C − amount) times → drops one item per click
     *   3. Left-click hotbar slot  → places remaining 'amount' items back
     */
    private void splitStack(int hotbarSlot, int count, int amount, Runnable callback) {
        runOnMain(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || !(client.currentScreen instanceof InventoryScreen)) {
                cycleRunning.set(false);
                return;
            }

            PlayerScreenHandler handler = client.player.playerScreenHandler;

            // Find first empty slot in main inventory (handler indices 9–35)
            int emptySlot = -1;
            for (int i = 9; i <= 35; i++) {
                if (handler.slots.get(i).getStack().isEmpty()) {
                    emptySlot = i;
                    break;
                }
            }

            if (emptySlot == -1) {
                // No room → failback
                client.currentScreen.close();
                SellHelperConfig cfg = SellHelperConfig.get();
                scheduleAfter(() -> doFailback(cfg), rnd(150, 350));
                return;
            }

            int hotbarHandlerIdx = 36 + hotbarSlot;

            // 1. Pick up all items from hotbar slot
            client.interactionManager.clickSlot(
                    handler.syncId, hotbarHandlerIdx, 0, SlotActionType.PICKUP, client.player);

            // 2. Drop excess one-by-one into the empty slot
            for (int i = 0; i < (count - amount); i++) {
                client.interactionManager.clickSlot(
                        handler.syncId, emptySlot, 1, SlotActionType.PICKUP, client.player);
            }

            // 3. Place 'amount' items back in hotbar slot
            client.interactionManager.clickSlot(
                    handler.syncId, hotbarHandlerIdx, 0, SlotActionType.PICKUP, client.player);
        });

        scheduleAfter(callback, rnd(150, 300));
    }

    // --------------------------------------------------- sell

    private void doSell(SellHelperConfig cfg) {
        runOnMain(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            client.player.networkHandler.sendChatCommand("ah sell " + cfg.price);
        });
        // Release the cycle lock after the sell delay; the purchase event will restart it
        scheduleAfter(() -> cycleRunning.set(false), rnd(800, 1200));
    }

    // --------------------------------------------------- inventory helpers

    private void openInventory(Runnable callback) {
        runOnMain(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.setScreen(new InventoryScreen(client.player));
            }
        });
        scheduleAfter(callback, rnd(200, 400));
    }

    private void closeInventory(Runnable callback) {
        runOnMain(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null) {
                client.currentScreen.close();
            }
        });
        scheduleAfter(callback, rnd(150, 350));
    }

    private void switchHotbarSlot(int slot, Runnable callback) {
        runOnMain(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            client.player.getInventory().selectedSlot = slot;
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        });
        scheduleAfter(callback, rnd(100, 200));
    }

    /**
     * Opens inventory, moves item from main-inventory slot to a hotbar slot,
     * then closes the inventory.  Uses two left-clicks: pick up from invSlot,
     * place in hotbar slot.
     */
    private void moveInvToHotbar(int invSlot, int hotbarSlot, Runnable callback) {
        openInventory(() -> {
            runOnMain(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player == null || !(client.currentScreen instanceof InventoryScreen)) {
                    cycleRunning.set(false);
                    return;
                }
                PlayerScreenHandler handler = client.player.playerScreenHandler;
                // invSlot 9–35 maps directly to handler index 9–35
                client.interactionManager.clickSlot(
                        handler.syncId, invSlot, 0, SlotActionType.PICKUP, client.player);
                // hotbarSlot 0–8 maps to handler index 36–44
                client.interactionManager.clickSlot(
                        handler.syncId, 36 + hotbarSlot, 0, SlotActionType.PICKUP, client.player);
            });
            scheduleAfter(() -> closeInventory(callback), rnd(100, 300));
        });
    }

    // --------------------------------------------------- failback

    private void doFailback(SellHelperConfig cfg) {
        runOnMain(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            client.inGameHud.getChatHud().addMessage(
                    Text.literal("§e[SellHelper] §fОсвободите хранилище или уберите предметы с продажи")
            );
            client.player.networkHandler.sendChatCommand("ah resel");
        });

        cycleRunning.set(false);
        stopReselTimer();

        // Repeat /ah resel every 35 seconds while waiting for a purchase
        reselTimer = scheduler.scheduleAtFixedRate(() ->
                runOnMain(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && active.get()) {
                        client.player.networkHandler.sendChatCommand("ah resel");
                    }
                }),
                35, 35, TimeUnit.SECONDS
        );
    }

    // --------------------------------------------------- all sold

    private void doAllSold() {
        active.set(false);
        cycleRunning.set(false);
        stopReselTimer();
        scheduler.execute(this::sendSystemNotification);
    }

    private void sendSystemNotification() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            TrayIcon icon = new TrayIcon(img, "SellHelper");
            icon.setImageAutoSize(true);
            tray.add(icon);
            icon.displayMessage("SellHelper", "Все товары проданы! \uD83C\uDF89", TrayIcon.MessageType.INFO);
            Thread.sleep(8_000);
            tray.remove(icon);
        } catch (Exception ignored) {
        }
    }

    // --------------------------------------------------- utilities

    private void stopReselTimer() {
        ScheduledFuture<?> t = reselTimer;
        if (t != null) {
            t.cancel(false);
            reselTimer = null;
        }
    }

    private boolean isTarget(ItemStack stack, SellHelperConfig cfg) {
        if (stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.toString().equals(cfg.itemId);
    }

    private int findFreeHotbarSlot(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private void runOnMain(Runnable task) {
        MinecraftClient.getInstance().execute(task);
    }

    private void scheduleAfter(Runnable task, long delayMs) {
        scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Returns a random long in [min, max]. */
    private long rnd(long min, long max) {
        return min + ThreadLocalRandom.current().nextLong(max - min + 1);
    }
}
