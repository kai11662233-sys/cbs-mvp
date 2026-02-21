package com.example.cbs_mvp;

import com.microsoft.playwright.Page;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VerificationUtils {

    public static void takeScreenshot(Page page, String name) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "verification_" + name + "_" + timestamp + ".png";
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get(filename))
                .setFullPage(true));
        System.out.println("Saved screenshot: " + filename);
    }
}
