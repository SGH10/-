package com.zijianxin.website.workflow;

import org.springframework.stereotype.Service;

@Service
public class WorkflowService {

    private final WorkflowSearchService workflowSearchService;
    private final WorkflowDraftService workflowDraftService;
    private final WorkflowEmailService workflowEmailService;
    private final SettingsService settingsService;

    public WorkflowService(
            WorkflowSearchService workflowSearchService,
            WorkflowDraftService workflowDraftService,
            WorkflowEmailService workflowEmailService,
            SettingsService settingsService
    ) {
        this.workflowSearchService = workflowSearchService;
        this.workflowDraftService = workflowDraftService;
        this.workflowEmailService = workflowEmailService;
        this.settingsService = settingsService;
    }

    public synchronized WorkflowModels.CustomerSearchResponse searchCustomers(WorkflowModels.CustomerSearchRequest request) {
        return workflowSearchService.searchCustomers(request);
    }

    public WorkflowModels.CustomerSearchResponse getLastSearchResponse() {
        return workflowSearchService.getLastSearchResponse();
    }

    public WorkflowModels.DraftResponse generateDraft(WorkflowModels.DraftRequest request) {
        return workflowDraftService.generateDraft(request);
    }

    public WorkflowModels.DraftResponse optimizeDraft(WorkflowModels.DraftOptimizationRequest request) {
        return workflowDraftService.optimizeDraft(request);
    }

    public WorkflowModels.TranslateEmailResponse translateEmail(WorkflowModels.TranslateEmailRequest request) {
        return workflowDraftService.translateEmail(request);
    }

    public WorkflowModels.SendEmailResponse sendEmail(WorkflowModels.SendEmailRequest request) {
        return workflowEmailService.sendEmail(request);
    }

    public SettingsModels.AppSettings getSettings() {
        return settingsService.getSettings();
    }

    public SettingsModels.AiSettings saveAiSettings(SettingsModels.AiSettings request) {
        return settingsService.saveAiSettings(request);
    }

    public SettingsModels.SearchSettings saveSearchSettings(SettingsModels.SearchSettings request) {
        return settingsService.saveSearchSettings(request);
    }

    public SettingsModels.CrawlerSettings saveCrawlerSettings(SettingsModels.CrawlerSettings request) {
        return settingsService.saveCrawlerSettings(request);
    }

    public SettingsModels.MailSettings saveMailSettings(SettingsModels.MailSettings request) {
        return settingsService.saveMailSettings(request);
    }

    public SettingsModels.GeneralSettings saveGeneralSettings(SettingsModels.GeneralSettings request) {
        return settingsService.saveGeneralSettings(request);
    }
}
