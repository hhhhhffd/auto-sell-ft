package com.sellhelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class SellHelperConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("sellhelper.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String itemId = "minecraft:air";
    public int amount = 1;
    public long price = 0;

    private static SellHelperConfig instance;

    public static SellHelperConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static SellHelperConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                SellHelperConfig cfg = GSON.fromJson(reader, SellHelperConfig.class);
                return cfg != null ? cfg : new SellHelperConfig();
            } catch (Exception e) {
                return new SellHelperConfig();
            }
        }
        return new SellHelperConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
