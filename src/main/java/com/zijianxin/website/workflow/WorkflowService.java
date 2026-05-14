package com.zijianxin.website.workflow;

import org.springframework.stereotype.Service;

@Service
public class WorkflowService {

    private final WorkflowSearchService workflowSearchService;
    private final WorkflowDraftService workflowDraftService;
    private final WorkflowEmailService workflowEmailService;

    public WorkflowService(
            WorkflowSearchService workflowSearchService,
            WorkflowDraftService workflowDraftService,
            WorkflowEmailService workflowEmailService
    ) {
        this.workflowSearchService = workflowSearchService;
        this.workflowDraftService = workflowDraftService;
        this.workflowEmailService = workflowEmailService;
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

    public WorkflowModels.SendEmailResponse sendEmail(WorkflowModels.SendEmailRequest request) {
        return workflowEmailService.sendEmail(request);
    }
}
