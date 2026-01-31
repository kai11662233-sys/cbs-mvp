package com.example.cbs_mvp.discovery;

import java.nio.file.Paths;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.cbs_mvp.dto.discovery.CreateDiscoveryItemRequest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private DiscoveryService discoveryService;

    @Test
    void testDiscoveryUI() {
        // Seed Data
        CreateDiscoveryItemRequest req = new CreateDiscoveryItemRequest(
                "http://example.com/test-item",
                "E2E Test Item",
                "NEW",
                "OFFICIAL",
                "Test Category",
                new BigDecimal("5000"),
                null, new BigDecimal("1.5"), "Test Note");
        discoveryService.create(req);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // Show browser window
                    .setSlowMo(1000)); // Slow down operations for visibility
            Page page = browser.newPage();

            // 1. Login
            System.out.println("Navigating to Login...");
            page.navigate("http://localhost:" + port);

            page.fill("#username", "admin");
            page.fill("#password", "admin");
            page.click("button:has-text('ログイン')");

            // Wait for dashboard
            try {
                page.waitForSelector("#app", new Page.WaitForSelectorOptions().setTimeout(3000));
            } catch (Exception e) {
                System.out.println("Retry login with admin123");
                page.fill("#password", "admin123");
                page.click("button:has-text('ログイン')");
                page.waitForSelector("#app");
            }
            System.out.println("Login Successful");

            // 2. Click Discovery
            page.click("span[data-page='discovery']");
            page.waitForSelector("#discoveryTable tr td button"); // Wait for buttons to load
            System.out.println("Discovery Page Loaded");

            // 3. Screenshot List
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("discovery_page.png")));

            // 4. Open Detail Modal
            page.click("#discoveryTable tr td button:has-text('詳細')");
            page.waitForSelector("#discoveryModal.active");
            System.out.println("Discovery Modal Opened");

            // 5. Screenshot Modal
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("discovery_modal.png")));

            browser.close();
        }
    }
}
