package com.zijianxin.website.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/customer-search")
    public String customerSearchPage() {
        return "forward:/customer-search.html?v=3";
    }

    @GetMapping("/ai-outreach")
    public String aiOutreachPage() {
        return "forward:/ai-outreach.html?v=3";
    }

    @GetMapping("/crawler-settings")
    public String crawlerSettingsPage() {
        return "forward:/crawler-settings.html?v=3";
    }

    @GetMapping("/ai-settings")
    public String aiSettingsPage() {
        return "forward:/ai-settings.html?v=3";
    }

    @GetMapping("/mail-settings")
    public String mailSettingsPage() {
        return "forward:/mail-settings.html?v=3";
    }

    @GetMapping("/general-settings")
    public String generalSettingsPage() {
        return "forward:/general-settings.html?v=3";
    }

    @GetMapping("/crawler-rules")
    public String crawlerRulesPage() {
        return "forward:/crawler-rules.html?v=3";
    }
}
