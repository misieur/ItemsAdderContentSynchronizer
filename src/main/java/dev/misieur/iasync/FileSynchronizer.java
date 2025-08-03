package dev.misieur.iasync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.misieur.iasync.bstats.Metrics;
import dev.misieur.iasync.utils.FileUtils;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

public class FileSynchronizer {

    public static void synchronizeFiles(String zipUrl, String githubToken, ItemsAdderContentSynchronizer plugin, String tagName) {
        File zipFile = downloadRelease(zipUrl, githubToken, tagName, plugin);
        if (zipFile != null) {
            plugin.getLogger().info("Updating ItemsAdder's contents folder...");
            File contentFolder = new File("plugins/ItemsAdder/contents");
            plugin.getLogger().info("Deleting old content in: " + contentFolder.getPath());
            FileUtils.deleteDirectoryContent(contentFolder, ItemsAdderContentSynchronizer.getExclude_folders());
            plugin.getLogger().info("Unzipping: " + zipFile.getPath() + " into: " + contentFolder.getPath());
            try (ZipFile zip = new ZipFile(zipFile)) {
                String rootDir = zip.stream()
                        .map(entry -> entry.getName())
                        .filter(name -> name.contains("/"))
                        .findFirst()
                        .map(name -> name.substring(0, name.indexOf("/") + 1))
                        .orElse("");

                zip.stream().forEach(entry -> {
                    String entryName = entry.getName();
                    if (entryName.equals(rootDir.substring(0, rootDir.length() - 1))) {
                        return;
                    }
                    String relativePath = entryName.startsWith(rootDir) ?
                            entryName.substring(rootDir.length()) : entryName;

                    if (relativePath.isEmpty()) return;

                    for (String excludedFolder : ItemsAdderContentSynchronizer.getExclude_folders()) {
                        if (relativePath.startsWith(excludedFolder + "/") || relativePath.equals(excludedFolder)) {
                            return;
                        }
                    }

                    try (InputStream inputStream = zip.getInputStream(entry)) {
                        File outFile = new File(contentFolder, relativePath);
                        if (entry.isDirectory()) {
                            outFile.mkdirs();
                        } else {
                            outFile.getParentFile().mkdirs();
                            try (FileOutputStream outputStream = new FileOutputStream(outFile)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                            }
                        }
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to extract entry: " + entry.getName() + " - " + e.getMessage());
                    }
                });
                plugin.getLogger().info("✅ Files placed successfully!");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to place files from release: " + e.getMessage());
            }
            plugin.getLogger().info("Cleaning up download folder...");
            File downloadDir = new File(plugin.getDataFolder(), "downloads");
            FileUtils.deleteDirectory(downloadDir);
            plugin.getLogger().info("✅ Done!");
            File lastestReleaseFile = new File(plugin.getDataFolder(), "latest_release.txt");
            try (FileOutputStream fos = new FileOutputStream(lastestReleaseFile)) {
                fos.write(tagName.getBytes());
                plugin.getLogger().info("Saved latest release tag: " + tagName);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save latest release tag: " + e.getMessage());
            }
        }
    }

    private static File downloadRelease(String zipUrl, String githubToken, String tagName, ItemsAdderContentSynchronizer plugin) {
        try {
            URL url = new URL(zipUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + githubToken);
            connection.setRequestProperty("User-Agent", "ItemsAdderContentSynchronizer-Plugin");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                File downloadDir = new File(plugin.getDataFolder(), "downloads");
                if (!downloadDir.exists()) downloadDir.mkdirs();

                File zipFile = new File(downloadDir, tagName + ".zip");

                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(zipFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    plugin.getLogger().info("✅ Release downloaded: " + zipFile.getPath());
                    return zipFile;
                }
            } else {
                String errorMessage = connection.getResponseMessage();
                plugin.getLogger().severe("Failed to download release: HTTP " + responseCode + " - " + errorMessage);
            }

            connection.disconnect();

        } catch (Exception e) {
            plugin.getLogger().severe("Error downloading release: " + e.getMessage());
        }
        return null;
    }

    public static void synchronizeFilesIfNeeded(ItemsAdderContentSynchronizer plugin, String githubToken, String repoOwner, String repoName) throws IOException {
        String apiUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/latest";

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "ItemsAdderContentSynchronizer-Plugin");

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (InputStream is = connection.getInputStream()) {
                String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                String tagName = json.get("tag_name").getAsString();
                if (shouldSynchronize(plugin, tagName)) {
                    String zipUrl = json.get("zipball_url").getAsString();
                    plugin.getLogger().info("New release found: " + tagName);
                    plugin.getLogger().info("Downloading release from: " + zipUrl);
                    synchronizeFiles(zipUrl, githubToken, plugin, tagName);
                    plugin.getServer().getPluginManager().registerEvents(new Listener() {
                        @SuppressWarnings("UnstableApiUsage")
                        @EventHandler
                        public void onPluginEnable(PluginEnableEvent event) {
                            PluginMeta pluginMeta = event.getPlugin().getPluginMeta();
                            if ("ItemsAdder".equals(pluginMeta.getName())) {
                                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (task) -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "iazip"), 20L);
                            }
                        }
                    }, plugin);
                } else {
                    plugin.getLogger().info("No new updates available. Current version is up-to-date.");
                }
            }
        } else {
            String errorMessage = "Failed to get latest release: HTTP " + responseCode;
            try (InputStream is = connection.getErrorStream()) {
                if (is != null) {
                    errorMessage += "\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            throw new IOException(errorMessage);
        }
    }

    public static boolean shouldSynchronize(ItemsAdderContentSynchronizer plugin, String tagName) {
        File lastestReleaseFile = new File(plugin.getDataFolder(), "latest_release.txt");
        if (!lastestReleaseFile.exists()) {
            return true;
        }

        try (InputStream inputStream = lastestReleaseFile.toURI().toURL().openStream()) {
            String lastTagName = new String(inputStream.readAllBytes());
            return !lastTagName.equals(tagName);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read latest release tag: " + e.getMessage());
            return true;
        }
    }

}
