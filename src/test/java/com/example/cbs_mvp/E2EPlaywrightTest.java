package com.example.cbs_mvp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Locator;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class E2EPlaywrightTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CandidateRepository candidateRepo;

    @Test
    void interactiveUserFlow() {
        // Prepare Data
        Candidate c = new Candidate();
        c.setSourceUrl("http://example.com/playwright-test-" + System.currentTimeMillis());
        c.setSourcePriceYen(new BigDecimal("9800"));
        c.setWeightKg(new BigDecimal("2.5"));
        c.setSizeTier("L");
        c.setState("CANDIDATE");
        c = candidateRepo.save(c);
        Long candidateId = c.getCandidateId();
        System.out.println("Created test candidate ID: " + candidateId);

        try (Playwright playwright = Playwright.create()) {
            // Launch browser in HEADED mode for visibility
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setSlowMo(1000)); // Slow down by 1s per operation to observe

            Page page = browser.newPage();

            // 1. Navigate to Login Page
            String url = "http://localhost:" + port;
            System.out.println("Navigating to: " + url);
            page.navigate(url);

            // 2. Perform Login
            System.out.println("Logging in...");
            page.fill("#username", "admin"); // Default user
            page.fill("#password", "admin"); // Logic in AuthController defaults to 'admin' if no hash set
            // Note: AuthController uses SystemFlag or default hardcoded 'admin'/'admin'
            page.click("button:has-text('ログイン')");

            // 3. Wait for Dashboard to Load
            page.waitForSelector("#app", new Page.WaitForSelectorOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            System.out.println("Dashboard loaded.");

            // 4. Go to Candidates Page (Click Tab)
            page.click("span[data-page='candidates']");
            System.out.println("Clicked Candidates Tab.");

            // 5. Verify Candidate is in the list
            // We need to reload/wait for fetch
            page.waitForSelector("td:has-text('http://example.com/playwright-test')");
            System.out.println("Found created candidate in the list.");

            // 6. Select the candidate
            // Use specific value attribute which matches candidateId
            page.locator("input[value='" + candidateId + "']").check();
            System.out.println("Checked candidate checkbox for ID: " + candidateId);

            // 7. Click Bulk Action
            // Action bar should appear
            page.waitForSelector("#bulkActionBar");
            page.click("#bulkAutoDraft"); // 'Create Drafts if OK'
            page.click("button:has-text('Auto-Price Selected')");
            System.out.println("Clicked Auto-Price Selected.");

            // 8. Wait for completion (Simple wait or check for status change)
            // Just wait a bit to see the "Processing" logic if any
            page.waitForTimeout(2000);

            // Take Screenshot
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("playwright_interactive_evidence.png")));
            System.out.println("Screenshot saved.");

            browser.close();
        }
    }
}
