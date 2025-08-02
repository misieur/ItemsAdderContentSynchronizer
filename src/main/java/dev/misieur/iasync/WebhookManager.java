package dev.misieur.iasync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebhookManager {

    public static void createWebhook(String token, String repoOwner, String repoName, String webhookUrl, String secret) throws IOException {
        String apiUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/hooks";

        String payload = """
        {
          "name": "web",
          "active": true,
          "events": ["release"],
          "config": {
            "url": "%s",
            "content_type": "json",
            "secret": "%s",
            "insecure_ssl": "0"
          }
        }
        """.formatted(webhookUrl, secret);

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            String errorMessage = "Failed to create webhook: HTTP " + responseCode;
            try (InputStream is = connection.getErrorStream()) {
                if (is != null) {
                    errorMessage += "\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            throw new IOException(errorMessage);
        }
    }

    public static void deleteWebhooksByUrl(String token, String repoOwner, String repoName, String targetUrl) throws IOException {
        String apiUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/hooks";

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("Failed to fetch webhooks: HTTP " + responseCode);
        }

        String response;
        try (InputStream is = connection.getInputStream()) {
            response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        JsonArray webhooks = JsonParser.parseString(response).getAsJsonArray();
        for (int i = 0; i < webhooks.size(); i++) {
            JsonObject webhook = webhooks.get(i).getAsJsonObject();
            JsonObject config = webhook.getAsJsonObject("config");

            if (config != null && config.has("url")) {
                String webhookUrl = config.get("url").getAsString();
                if (targetUrl.equals(webhookUrl)) {
                    int webhookId = webhook.get("id").getAsInt();
                    deleteWebhook(token, repoOwner, repoName, webhookId);
                }
            }
        }
    }

    private static void deleteWebhook(String token, String repoOwner, String repoName, int webhookId) throws IOException {
        String apiUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/hooks/" + webhookId;

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("Failed to delete webhook " + webhookId + ": HTTP " + responseCode);
        }
    }

}
