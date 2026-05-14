package com.zijianxin.website.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/customer-search")
    public String customerSearchPage() {
        return "forward:/customer-search.html";
    }

    @GetMapping("/ai-outreach")
    public String aiOutreachPage() {
        return "forward:/ai-outreach.html";
    }

    @GetMapping("/crawler-settings")
    public String crawlerSettingsPage() {
        return "forward:/crawler-settings.html";
    }

    @GetMapping("/ai-settings")
    public String aiSettingsPage() {
        return "forward:/ai-settings.html";
    }

    @GetMapping("/mail-settings")
    public String mailSettingsPage() {
        return "forward:/mail-settings.html";
    }

    @GetMapping("/general-settings")
    public String generalSettingsPage() {
        return "forward:/general-settings.html";
    }

    @GetMapping("/crawler-rules")
    public String crawlerRulesPage() {
        return "forward:/crawler-rules.html";
    }
}
