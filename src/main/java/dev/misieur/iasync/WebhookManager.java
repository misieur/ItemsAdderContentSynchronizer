package dev.misieur.iasync;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebhookCreator {

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
        if (responseCode >= 200 && responseCode < 300) {
            System.out.println("✅ Webhook created successfully");
        } else {
            System.err.println("❌ Failed to create webhook: HTTP " + responseCode);
            try (InputStream is = connection.getErrorStream()) {
                if (is != null) System.err.println(new String(is.readAllBytes()));
            }
        }
    }

}
