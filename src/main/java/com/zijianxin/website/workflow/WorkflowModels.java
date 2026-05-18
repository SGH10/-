package com.zijianxin.website.workflow;

import java.util.List;

public final class WorkflowModels {

    private WorkflowModels() {
    }

    public record CustomerSearchRequest(
            String industry,
            String market,
            String keywords,
            String companySize,
            Integer requestedLimit
    ) {
    }

    public record SearchStats(
            int totalCustomers,
            int emailCount,
            int highMatchCount,
            int marketCoverage
    ) {
    }

    public record SearchLogEntry(
            String time,
            String message
    ) {
    }

    public record CustomerLead(
            String id,
            String companyName,
            String website,
            String country,
            String contactName,
            String email,
            String channel,
            String fitNote
    ) {
    }

    public record CustomerSearchResponse(
            String summary,
            SearchStats stats,
            List<SearchLogEntry> logs,
            List<CustomerLead> customers
    ) {
    }

    public record DraftRequest(
            String companyName,
            String productName,
            String valueProposition,
            String language,
            String tone,
            String callToAction,
            List<CustomerLead> recipients
    ) {
    }

    public record DraftResponse(
            String subject,
            String body,
            String analysis,
            List<String> followUpTips
    ) {
    }

    public record DraftOptimizationRequest(
            String subject,
            String body,
            String companyName,
            String productName,
            String valueProposition,
            String language,
            String tone,
            String callToAction,
            List<CustomerLead> recipients
    ) {
    }

    public record SendEmailRequest(
            String senderName,
            String senderEmail,
            String subject,
            String body,
            List<CustomerLead> recipients
    ) {
    }

    public record TranslateEmailRequest(
            String subject,
            String body,
            String targetLanguage
    ) {
    }

    public record TranslateEmailResponse(
            String subject,
            String body
    ) {
    }

    public record SendEmailResponse(
            int sentCount,
            String batchId,
            String senderEmail,
            String message,
            List<String> nextSteps
    ) {
    }
}
