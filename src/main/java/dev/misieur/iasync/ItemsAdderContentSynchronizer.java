package dev.misieur.iasync;

import com.sun.net.httpserver.HttpServer;
import dev.misieur.iasync.bstats.Metrics;
import io.papermc.paper.plugin.configuration.PluginMeta;
import lombok.Getter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;

public final class ItemsAdderContentSynchronizer extends JavaPlugin {

    private HttpServer server;
    private String webhookUrl;
    private String repoOwner;
    private String repoName;
    private String githubToken;
    @Getter
    private static List<String> exclude_folders;

    @Override
    public void onEnable() {
        // Save default config
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        int port = getConfig().getInt("webhook_port", 8080);

        // bstats Metrics setup
        Metrics metrics = new Metrics(this, 26744);

        // Get ItemsAdder version from PluginMeta when it is enabled
        getServer().getPluginManager().registerEvents(new Listener() {
            @SuppressWarnings("UnstableApiUsage")
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                PluginMeta pluginMeta = event.getPlugin().getPluginMeta();
                if ("ItemsAdder".equals(pluginMeta.getName())) {
                    String version = pluginMeta.getVersion();
                    metrics.addCustomChart(new Metrics.SimplePie("itemsadder_version", () -> version));
                }
            }
        }, this);

        // Verifications
        githubToken = getConfig().getString("github_token");
        if (githubToken == null || githubToken.isEmpty()) {
            getLogger().severe("No Github Token set in config.yml – the plugin can't work without it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Synchronize files if needed
        repoOwner = getConfig().getString("repo_owner");
        repoName = getConfig().getString("repo_name");
        try {
            FileSynchronizer.synchronizeFilesIfNeeded(this, githubToken, repoOwner, repoName);
        } catch (IOException e) {
            getLogger().severe("Failed to synchronize files: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getConfig().getBoolean("use_webhook", true)) {
            // Create a new GitHub webhook (and delete existing ones with the same URL)
            getLogger().info("Creating GitHub webhook...");
            String secret = generateSecret();
            webhookUrl = getConfig().getString("webhook_url");
            exclude_folders = getConfig().getStringList("exclude_folders");
            try {
                WebhookManager.deleteWebhooksByUrl(githubToken, repoOwner, repoName, webhookUrl);
                WebhookManager.createWebhook(githubToken, repoOwner, repoName, webhookUrl, secret);
            } catch (IOException e) {
                getLogger().severe("Failed to create GitHub webhook: " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Start the webhook server
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/github-release", new WebhookHandler(secret, this, githubToken));
                server.setExecutor(Executors.newSingleThreadExecutor());
                server.start();
                getLogger().info("Started GitHub webhook listener on http://*:" + port + "/github-release");
            } catch (IOException e) {
                getLogger().severe("Failed to start webhook server: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        // Stop the webhook server if it exists
        if (server != null) {
            server.stop(0);
            getLogger().info("Webhook server stopped.");
        }

        if (getConfig().getBoolean("use_webhook", true)) {
            // Delete the GitHub webhook
            try {
                WebhookManager.deleteWebhooksByUrl(githubToken, repoOwner, repoName, webhookUrl);
                getLogger().info("GitHub webhook deleted.");
            } catch (IOException e) {
                getLogger().severe("Failed to delete GitHub webhook: " + e.getMessage());
            }
        }
        getLogger().info("ItemsAdderContentSynchronizer disabled.");
    }

    private static String generateSecret() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
