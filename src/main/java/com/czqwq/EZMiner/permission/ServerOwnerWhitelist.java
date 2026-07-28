package com.czqwq.EZMiner.permission;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.czqwq.EZMiner.EZMiner;

/**
 * Per-world server-owner whitelist persisted into {@code <world>/data/ezminer/server_owners.list}.
 *
 * <p>
 * One lowercase player name per line. Managed via {@code /ezminer addserverowner / removeserverowner}.
 * The whitelist is loaded when the world starts and saved on every modification.
 */
public final class ServerOwnerWhitelist {

    private static final String DIR_NAME = "data/ezminer";
    private static final String FILE_NAME = "server_owners.list";

    private static final Set<String> owners = new LinkedHashSet<>();
    private static File file;

    private ServerOwnerWhitelist() {}

    /** Must be called once after the overworld is available (e.g. in {@code serverStarted}). */
    public static void init(File worldDirectory) {
        File dir = new File(worldDirectory, DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            EZMiner.LOG.warn("ServerOwnerWhitelist: failed to create directory {}", dir.getAbsolutePath());
        }
        file = new File(dir, FILE_NAME);
        load();
    }

    private static void load() {
        owners.clear();
        if (file == null || !file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim()
                    .toLowerCase();
                if (!name.isEmpty()) {
                    owners.add(name);
                }
            }
        } catch (IOException e) {
            EZMiner.LOG.warn("ServerOwnerWhitelist: failed to load from {}", file.getAbsolutePath(), e);
        }
    }

    private static void save() {
        if (file == null) {
            EZMiner.LOG.warn("ServerOwnerWhitelist: save skipped — not initialised (no world directory).");
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String name : owners) {
                writer.write(name);
                writer.newLine();
            }
        } catch (IOException e) {
            EZMiner.LOG.warn("ServerOwnerWhitelist: failed to save to {}", file.getAbsolutePath(), e);
        }
    }

    public static boolean contains(String playerName) {
        return owners.contains(playerName.toLowerCase());
    }

    public static Set<String> getAll() {
        return Collections.unmodifiableSet(owners);
    }

    public static boolean isEmpty() {
        return owners.isEmpty();
    }

    public static void add(String playerName) {
        owners.add(playerName.toLowerCase());
        save();
    }

    public static void remove(String playerName) {
        owners.remove(playerName.toLowerCase());
        save();
    }
}
