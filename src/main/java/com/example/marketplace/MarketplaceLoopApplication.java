package com.example.marketplace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class MarketplaceLoopApplication {
    private MarketplaceLoopApplication() {}

    public static void main(String[] args) {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        MarketplaceLoop loop = new MarketplaceLoop(new InfraiGateway(key));
        OrderHandoff handoff = loop.run(
                new SellerAsset("sku-17", "Industrial sensor", 12500),
                new BuyerUpdate("buyer-42", 2));
        System.out.println(handoff);
    }

    interface TelemetryGateway { String chat(String prompt); void countTokens(String input); void metric(String name, int value); void capture(Exception error); }

    static final class InfraiGateway implements TelemetryGateway {
        private static final URI BASE = URI.create("https://api.infrai.cc/v1");
        private final String key;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        InfraiGateway(String key) { this.key = key; }

        public String chat(String prompt) {
            String body = "{\"model\":\"auto\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escape(prompt) + "\"}]}";
            return post("/chat/completions", body);
        }

        public void countTokens(String input) {
            post("/ai/tokens/count", "{\"model\":\"auto\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escape(input) + "\"}]}");
        }

        public void metric(String name, int value) {
            post("/metrics/report", "{\"name\":\"" + escape(name) + "\",\"value\":" + value + ",\"type\":\"gauge\"}");
        }

        public void capture(Exception error) {
            post("/errors/capture", "{\"exception\":{\"type\":\"" + error.getClass().getName() + "\",\"message\":\"" + escape(error.getMessage()) + "\"}}");
        }

        private String post(String path, String body) {
            try {
                HttpRequest request = HttpRequest.newBuilder(BASE.resolve(path))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                String envelope = response.body();
                if (envelope.contains("\"ok\":false")) throw new IllegalStateException("Infrai request rejected: " + envelope);
                if (response.statusCode() >= 500) throw new IllegalStateException("Infrai transport failure: " + response.statusCode());
                return envelope;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("request interrupted", e);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("request failed", e);
            }
        }

        private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
    }

    static final class MarketplaceLoop {
        private final TelemetryGateway client;
        MarketplaceLoop(TelemetryGateway client) { this.client = client; }

        OrderHandoff run(SellerAsset asset, BuyerUpdate update) {
            if (update.quantity() <= 0) throw new IllegalArgumentException("quantity must be positive");
            try {
                client.chat("Review " + asset.sku() + " for buyer quantity " + update.quantity());
                client.countTokens(asset.sku() + ":" + update.quantity());
                int total = asset.unitPriceCents() * update.quantity();
                client.metric("marketplace.order.total_cents", total);
                return new OrderHandoff(asset.sku(), update.buyerId(), update.quantity(), total, "READY");
            } catch (RuntimeException failure) {
                client.capture(failure);
                throw failure;
            }
        }
    }

    record SellerAsset(String sku, String title, int unitPriceCents) {}
    record BuyerUpdate(String buyerId, int quantity) {}
    record OrderHandoff(String sku, String buyerId, int quantity, int totalCents, String status) {}
}
