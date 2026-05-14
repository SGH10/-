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

    @PostMapping("/outreach/send")
    public WorkflowModels.SendEmailResponse sendEmail(
            @RequestBody WorkflowModels.SendEmailRequest request
    ) {
        return workflowService.sendEmail(request);
    }
}
