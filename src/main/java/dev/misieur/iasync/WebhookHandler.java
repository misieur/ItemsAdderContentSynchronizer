package dev.misieur.iasync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class WebhookHandler implements HttpHandler {

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String EVENT_HEADER = "X-GitHub-Event";
    private final byte[] secretKey;
    private final ItemsAdderContentSynchronizer plugin;
    private final String githubToken;

    public WebhookHandler(String secret, ItemsAdderContentSynchronizer plugin, String githubToken) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.plugin = plugin;
        this.githubToken = githubToken;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String sigHeader = exchange.getRequestHeaders().getFirst(SIGNATURE_HEADER);
        String event = exchange.getRequestHeaders().getFirst(EVENT_HEADER);
        if (sigHeader == null || event == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        byte[] body = exchange.getRequestBody().readAllBytes();
        if (!isValidSignature(sigHeader, body)) {
            plugin.getLogger().warning("Invalid signature from GitHub");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        if ("release".equals(event) &&
                json.has("action") &&
                "published".equals(json.get("action").getAsString()) &&
                json.has("release")) {

            JsonObject release = json.getAsJsonObject("release");
            String tagName = release.get("tag_name").getAsString();
            String htmlUrl = release.get("html_url").getAsString();
            String zipUrl = release.get("zipball_url").getAsString();
            plugin.getLogger().info("🎉 New GitHub release: " + tagName + " → " + htmlUrl);
            plugin.getLogger().info("Download the release zip from: " + zipUrl);
            FileSynchronizer.synchronizeFiles(zipUrl, githubToken, plugin, tagName);
            plugin.getLogger().info("Reloading ItemsAdder plugin to apply new contents...");
            Bukkit.getGlobalRegionScheduler().run(plugin, (task) -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "iazip"));

        }

        exchange.sendResponseHeaders(204, -1);
    }

    private boolean isValidSignature(String sigHeader, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(body);

            String calculated = "sha256=" + toHex(digest);
            return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.US_ASCII),
                    sigHeader.getBytes(StandardCharsets.US_ASCII));

        } catch (Exception e) {
            plugin.getLogger().severe("Error verifying signature: " + e.getMessage());
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}