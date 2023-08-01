package net.essentialsx;

import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
  public static final FileFilter YML_FILTER = pathname -> pathname.isFile() && pathname.getName().endsWith(".yml");
  private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[^a-z0-9]");

  public static void main(String[] args) throws IOException {
    dumpMaps();
  }

  public static void attemptMapBuild() throws ConfigurateException {
    final File userdataFolder = new File("userdata");

    if (!userdataFolder.isDirectory()) {
      throw new IllegalArgumentException("Missing userdata folder.");
    }

    final Map<UUID, Long> uuids = new HashMap<>();
    final Map<String, UUID> nameToUuidMap = new HashMap<>();

    final File[] files = userdataFolder.listFiles(YML_FILTER);
    if (files == null) {
      throw new IllegalArgumentException("You got nothin in there dawg.");
    }

    for (final File file : files) {
      final String fileName = file.getName();
      final UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 4));
      final YamlConfigurationLoader loader = configLoader(file);
      final ConfigurationNode node = loader.load();

      final String name = node.node("last-account-name").getString();
      if (name == null) {
        System.out.println("Account name not found for uuid: " + uuid);
      } else {
        if (INVALID_NAME_CHARS.matcher(name).matches()) {
          System.out.println("UUID, " + uuid + ", had name, " + name + ", with illegal characters. Could be conformed if safe usermap is enabled.");
        }

        final long time = node.node("timestamps.logout").getLong(0L);
        if (nameToUuidMap.containsKey(name)) {
          final UUID oldUuid = nameToUuidMap.get(name);
          if (oldUuid.version() < uuid.version() || (oldUuid.version() == uuid.version() && uuids.get(oldUuid) < time)) {
            System.out.println("New UUID found for " + name + ": " + uuid + " (old: " + oldUuid + "). Replacing.");
            uuids.remove(oldUuid);
          } else {
            System.out.println("Found UUID for " + name + ": " + uuid + " (old: " + oldUuid + "). Skipping.");
            continue;
          }
        }

        uuids.put(uuid, time);
        nameToUuidMap.put(name, uuid);
      }
    }

    for (Map.Entry<String, UUID> entry : nameToUuidMap.entrySet()) {
      System.out.println(entry.getKey() + " => " + entry.getValue());
    }

    System.out.println("Total Users in Name => UUID Cache: " + nameToUuidMap.size());
    System.out.println("Total Users in Known UUID Cache: " + uuids.size());
  }

  public static void dumpMaps() throws IOException {
    final Map<String, UUID> nameToUuidMap = new HashMap<>();
    final Set<UUID> uuidCache = new HashSet<>();

    final File mapCacheFile = new File("usermap.bin");
    final File uuidCacheFile = new File("uuids.bin");

    if (!mapCacheFile.exists() || !uuidCacheFile.exists()) {
      throw new IllegalArgumentException("Missing usermap or uuid caches.");
    }

    try (final DataInputStream dis = new DataInputStream(new FileInputStream(mapCacheFile))) {
      while (dis.available() > 0) {
        final String username = dis.readUTF();
        final UUID uuid = new UUID(dis.readLong(), dis.readLong());
        final UUID previous = nameToUuidMap.put(username, uuid);
        if (previous != null) {
          System.out.println("Replaced UUID during cache load for " + username + ": " + previous + " -> " + uuid);
        }
      }
    }

    try (final DataInputStream dis = new DataInputStream(new FileInputStream(uuidCacheFile))) {
      while (dis.available() > 0) {
        final UUID uuid = new UUID(dis.readLong(), dis.readLong());
        if (uuidCache.contains(uuid)) {
          System.out.println("UUID " + uuid + " duplicated in cache");
        }
        uuidCache.add(uuid);
      }
    }

    for (Map.Entry<String, UUID> entry : nameToUuidMap.entrySet()) {
      System.out.println(entry.getKey() + " => " + entry.getValue());
    }

    System.out.println("Total Users in Name => UUID Cache: " + nameToUuidMap.size());
    System.out.println("Total Users in Known UUID Cache: " + uuidCache.size());
  }

  private static YamlConfigurationLoader configLoader(final File file) {
    return YamlConfigurationLoader.builder()
        .defaultOptions(opts -> opts
            .header(null))
        .headerMode(HeaderMode.PRESET)
        .nodeStyle(NodeStyle.BLOCK)
        .indent(2)
        .file(file)
        .build();
  }
}