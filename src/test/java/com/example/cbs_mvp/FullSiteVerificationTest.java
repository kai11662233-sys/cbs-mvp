package com.example.cbs_mvp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.microsoft.playwright.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FullSiteVerificationTest {

    @LocalServerPort
    private int port;

    @Test
    void verifyAllPages() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setSlowMo(500));
            Page page = browser.newPage();

            // 1. Visit Home (Login)
            page.navigate("http://localhost:" + port + "/");
            VerificationUtils.takeScreenshot(page, "login_page");

            // 2. Login
            page.fill("#username", "admin");
            page.fill("#password", "admin");
            page.click("button:has-text('ログイン')");
            page.waitForSelector("#app", new Page.WaitForSelectorOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            VerificationUtils.takeScreenshot(page, "dashboard");

            // 3. Visit Candidates
            page.click("span[data-page='candidates']");
            page.waitForTimeout(1000);
            VerificationUtils.takeScreenshot(page, "candidates_page");

            // 4. Visit Orders
            page.click("span[data-page='orders']");
            page.waitForTimeout(1000);
            VerificationUtils.takeScreenshot(page, "orders_page");

            // 5. Visit Pricing
            // (Assuming there is a navigation link or button for it if it's a main page,
            // otherwise skip)

            // 6. Visit Discovery
            page.click("span[data-page='discovery']");
            page.waitForTimeout(2000); // Wait for external fetch if any
            VerificationUtils.takeScreenshot(page, "discovery_page");

            browser.close();
        }
    }
}
