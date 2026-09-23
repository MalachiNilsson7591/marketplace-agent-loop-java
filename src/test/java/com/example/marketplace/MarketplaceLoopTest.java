package com.example.marketplace;

public final class MarketplaceLoopTest {
    public static void main(String[] args) {
        var gateway = new MarketplaceLoopApplication.TelemetryGateway() {
            public String chat(String prompt) { return "{\"ok\":true}"; }
            public void countTokens(String input) { }
            public void metric(String name, int value) { }
            public void capture(Exception error) { throw new AssertionError(error); }
        };
        var loop = new MarketplaceLoopApplication.MarketplaceLoop(gateway);
        var result = loop.run(new MarketplaceLoopApplication.SellerAsset("sku-17", "Sensor", 12500),
                new MarketplaceLoopApplication.BuyerUpdate("buyer-42", 2));
        if (result.totalCents() != 25000 || !result.status().equals("READY")) {
            throw new AssertionError("order handoff decision is incorrect: " + result);
        }
        System.out.println("PASS: quantity is priced and handed off as READY");
    }
}
