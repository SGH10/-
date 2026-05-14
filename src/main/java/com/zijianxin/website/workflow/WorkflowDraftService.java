package com.zijianxin.website.workflow;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowDraftService {

    public WorkflowModels.DraftResponse generateDraft(WorkflowModels.DraftRequest request) {
        String companyName = fallback(request.companyName(), "LeadFlow Studio");
        String productName = fallback(request.productName(), "your solution");
        String valueProposition = fallback(
                request.valueProposition(),
                "We help teams shorten sourcing cycles, improve reply rates, and manage leads in one workflow."
        );
        String language = fallback(request.language(), "zh-CN");
        String tone = fallback(request.tone(), "professional");
        String callToAction = fallback(request.callToAction(), "如果方便，我们可以安排一个 15 分钟的线上沟通。");

        List<WorkflowModels.CustomerLead> recipients = request.recipients() == null ? List.of() : request.recipients();
        WorkflowModels.CustomerLead firstRecipient = recipients.isEmpty()
                ? new WorkflowModels.CustomerLead(
                "placeholder",
                "Selected Prospect",
                "",
                "目标市场",
                "Business Contact",
                "",
                "官网",
                "根据搜索结果生成"
        )
                : recipients.get(0);

        String subject = buildSubject(language, companyName, productName, firstRecipient.companyName());
        String body = buildBody(language, tone, companyName, productName, valueProposition, callToAction, firstRecipient, recipients.size());

        String analysis = "当前草稿围绕产品价值和目标客户场景生成，建议先小批量发送，再根据回复率继续调整。";
        List<String> followUpTips = List.of(
                "首封邮件发送后 3 天内追加一封简短跟进邮件。",
                "对高匹配客户补充案例、报价或资质文件，提高回复率。",
                "将已发送客户同步到 CRM，避免重复触达。"
        );

        return new WorkflowModels.DraftResponse(subject, body, analysis, followUpTips);
    }

    private String buildSubject(String language, String companyName, String productName, String recipientCompany) {
        return switch (language) {
            case "en" -> recipientCompany + " x " + companyName + " | A quick idea for " + productName;
            case "de" -> recipientCompany + " und " + companyName + " | Idee fuer " + productName;
            case "fr" -> recipientCompany + " x " + companyName + " | Une idee pour " + productName;
            case "es" -> recipientCompany + " y " + companyName + " | Idea rapida para " + productName;
            default -> recipientCompany + " x " + companyName + " | 关于 " + productName + " 的合作建议";
        };
    }

    private String buildBody(
            String language,
            String tone,
            String companyName,
            String productName,
            String valueProposition,
            String callToAction,
            WorkflowModels.CustomerLead recipient,
            int recipientCount
    ) {
        String greetingName = fallback(recipient.contactName(), "there");
        String englishToneLine = switch (tone) {
            case "warm" -> "I wanted to reach out in a warm and straightforward way.";
            case "direct" -> "I will get straight to the point and keep this brief.";
            case "consultative" -> "I wanted to share one market opportunity we have noticed.";
            default -> "I wanted to briefly introduce our solution.";
        };

        if ("en".equals(language)) {
            return String.join("\n\n",
                    "Hi " + greetingName + ",",
                    englishToneLine,
                    "I noticed that " + recipient.companyName() + " is active in the " + recipient.country() + " market. We are " + companyName + ", and we help teams like yours with " + productName + ".",
                    valueProposition,
                    "This draft is prepared for a batch of " + recipientCount + " selected prospects, and " + recipient.companyName() + " is one of the best matches.",
                    callToAction,
                    "Best regards,\n" + companyName
            );
        }

        return String.join("\n\n",
                greetingName + "，你好：",
                switch (tone) {
                    case "warm" -> "想先用更真诚直接的方式和你打个招呼。";
                    case "direct" -> "我直接说重点，希望这封邮件能节省你的判断时间。";
                    case "consultative" -> "先分享一个我们观察到的市场机会，看看是否对你们有帮助。";
                    default -> "想借这封邮件简要介绍一下我们的方案。";
                },
                "我们留意到 " + recipient.companyName() + " 正在 " + recipient.country() + " 市场推进业务，因此想向你介绍一下 " + companyName + " 的 " + productName + " 方案。",
                valueProposition,
                "当前这封开发信会同步用于 " + recipientCount + " 个已筛选客户，但我会优先把 " + recipient.companyName() + " 作为重点对象处理。",
                callToAction,
                "祝好，\n" + companyName
        );
    }

    private String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value.trim();
    }
}
