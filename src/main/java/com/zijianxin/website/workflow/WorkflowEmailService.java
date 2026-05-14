package com.zijianxin.website.workflow;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WorkflowEmailService {

    public WorkflowModels.SendEmailResponse sendEmail(WorkflowModels.SendEmailRequest request) {
        int sentCount = request.recipients() == null ? 0 : request.recipients().size();
        String senderEmail = fallback(request.senderEmail(), "");
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        List<String> nextSteps = List.of(
                "24 小时后检查打开率与回复率，筛选高意向客户。",
                "对未回复客户安排第二封跟进邮件，调整切入角度。",
                "把本次发送批次同步到 CRM，准备后续多渠道触达。"
        );

        return new WorkflowModels.SendEmailResponse(
                sentCount,
                batchId,
                senderEmail,
                "当前仍为模拟发送流程，尚未接入真实邮件服务。",
                nextSteps
        );
    }

    private String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value.trim();
    }
}
