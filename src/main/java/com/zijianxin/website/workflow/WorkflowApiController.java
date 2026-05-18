package com.zijianxin.website.workflow;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkflowApiController {

    private final WorkflowService workflowService;

    public WorkflowApiController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/customers/last-search")
    public ResponseEntity<WorkflowModels.CustomerSearchResponse> lastSearch() {
        WorkflowModels.CustomerSearchResponse lastSearch = workflowService.getLastSearchResponse();
        if (lastSearch == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(lastSearch);
    }

    @PostMapping("/customers/search")
    public WorkflowModels.CustomerSearchResponse searchCustomers(
            @RequestBody WorkflowModels.CustomerSearchRequest request
    ) {
        return workflowService.searchCustomers(request);
    }

    @PostMapping("/outreach/draft")
    public WorkflowModels.DraftResponse generateDraft(
            @RequestBody WorkflowModels.DraftRequest request
    ) {
        return workflowService.generateDraft(request);
    }

    @PostMapping("/outreach/optimize")
    public WorkflowModels.DraftResponse optimizeDraft(
            @RequestBody WorkflowModels.DraftOptimizationRequest request
    ) {
        return workflowService.optimizeDraft(request);
    }

    @PostMapping("/outreach/send")
    public WorkflowModels.SendEmailResponse sendEmail(
            @RequestBody WorkflowModels.SendEmailRequest request
    ) {
        return workflowService.sendEmail(request);
    }

    @PostMapping("/outreach/translate")
    public WorkflowModels.TranslateEmailResponse translateEmail(
            @RequestBody WorkflowModels.TranslateEmailRequest request
    ) {
        return workflowService.translateEmail(request);
    }

    @GetMapping("/settings")
    public SettingsModels.AppSettings getSettings() {
        return workflowService.getSettings();
    }

    @PostMapping("/settings/ai")
    public SettingsModels.AiSettings saveAiSettings(@RequestBody SettingsModels.AiSettings request) {
        return workflowService.saveAiSettings(request);
    }

    @PostMapping("/settings/search")
    public SettingsModels.SearchSettings saveSearchSettings(@RequestBody SettingsModels.SearchSettings request) {
        return workflowService.saveSearchSettings(request);
    }

    @PostMapping("/settings/crawler")
    public SettingsModels.CrawlerSettings saveCrawlerSettings(@RequestBody SettingsModels.CrawlerSettings request) {
        return workflowService.saveCrawlerSettings(request);
    }

    @PostMapping("/settings/mail")
    public SettingsModels.MailSettings saveMailSettings(@RequestBody SettingsModels.MailSettings request) {
        return workflowService.saveMailSettings(request);
    }

    @PostMapping("/settings/general")
    public SettingsModels.GeneralSettings saveGeneralSettings(@RequestBody SettingsModels.GeneralSettings request) {
        return workflowService.saveGeneralSettings(request);
    }
}
