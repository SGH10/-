package com.zijianxin.website.workflow;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

@Service
public class WorkflowEmailService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEmailService.class);

    private final SettingsService settingsService;

    public WorkflowEmailService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public WorkflowModels.SendEmailResponse sendEmail(WorkflowModels.SendEmailRequest request) {
        SettingsModels.MailSettings mail = settingsService.getSettings().mail();

        List<WorkflowModels.CustomerLead> recipients = request.recipients() == null ? List.of() : request.recipients();
        if (recipients.isEmpty()) {
            throw new IllegalStateException("No recipients selected for email sending.");
        }

        String senderEmail = fallback(request.senderEmail(), mail.senderEmail());
        String senderName = fallback(request.senderName(), mail.senderName());
        String subject = request.subject() == null ? "" : request.subject().trim();
        String body = request.body() == null ? "" : request.body().trim();
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        log.info("Email send requested. mode={}, batchId={}, recipients={}", mail.sendMode(), batchId, recipients.size());

        if (!"smtp".equalsIgnoreCase(mail.sendMode())) {
            log.info("Demo email mode active. sender={}, batchId={}", senderEmail, batchId);
            return new WorkflowModels.SendEmailResponse(
                    recipients.size(),
                    batchId,
                    senderEmail,
                    "当前仍是演示发送模式，未真正发出邮件。切换到 SMTP 模式后会进行真实发送。",
                    buildNextSteps()
            );
        }

        validateMailConfiguration(mail, senderEmail, senderName, subject, body);
        JavaMailSender javaMailSender = buildMailSender(mail);

        int sentCount = 0;
        List<String> failures = new ArrayList<>();

        for (WorkflowModels.CustomerLead recipient : recipients) {
            String to = recipient.email() == null ? "" : recipient.email().trim();
            if (to.isBlank()) {
                failures.add(recipient.companyName() + "：缺少邮箱地址");
                continue;
            }

            try {
                MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
                helper.setFrom(senderEmail, senderName);
                if (mail.replyToEmail() != null && !mail.replyToEmail().isBlank()) {
                    helper.setReplyTo(mail.replyToEmail().trim());
                }
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(appendSignature(body, mail.signature()), false);

                javaMailSender.send(mimeMessage);
                sentCount++;
                log.info("Email sent successfully. batchId={}, to={}", batchId, to);
            } catch (MailAuthenticationException exception) {
                log.error("SMTP authentication failed. batchId={}, to={}", batchId, to, exception);
                throw new IllegalStateException("SMTP authentication failed. Please check username, password, and security settings.", exception);
            } catch (MailException exception) {
                log.error("SMTP send failed. batchId={}, to={}", batchId, to, exception);
                failures.add(recipient.companyName() + "：" + compactMailError(exception));
            } catch (Exception exception) {
                log.error("Unexpected SMTP send failure. batchId={}, to={}", batchId, to, exception);
                failures.add(recipient.companyName() + "：发送失败");
            }
        }

        if (sentCount == 0) {
            throw new IllegalStateException("SMTP send failed for all recipients. " + String.join("；", failures));
        }

        String message = failures.isEmpty()
                ? "邮件发送成功。"
                : "部分邮件发送成功，失败明细：" + String.join("；", failures);

        return new WorkflowModels.SendEmailResponse(
                sentCount,
                batchId,
                senderEmail,
                message,
                buildNextSteps()
        );
    }

    private void validateMailConfiguration(
            SettingsModels.MailSettings mail,
            String senderEmail,
            String senderName,
            String subject,
            String body
    ) {
        if (mail.smtpHost() == null || mail.smtpHost().isBlank()) {
            throw new IllegalStateException("SMTP host is required.");
        }
        if (mail.smtpPort() <= 0) {
            throw new IllegalStateException("SMTP port is invalid.");
        }
        if (mail.smtpUsername() == null || mail.smtpUsername().isBlank()) {
            throw new IllegalStateException("SMTP username is required.");
        }
        if (mail.smtpPassword() == null || mail.smtpPassword().isBlank()) {
            throw new IllegalStateException("SMTP password or app password is required.");
        }
        if (senderEmail.isBlank()) {
            throw new IllegalStateException("Sender email is required.");
        }
        if (senderName.isBlank()) {
            throw new IllegalStateException("Sender name is required.");
        }
        if (subject.isBlank()) {
            throw new IllegalStateException("Email subject is required.");
        }
        if (body.isBlank()) {
            throw new IllegalStateException("Email body is required.");
        }
    }

    private JavaMailSender buildMailSender(SettingsModels.MailSettings mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.smtpHost().trim());
        sender.setPort(mail.smtpPort());
        sender.setUsername(mail.smtpUsername().trim());
        sender.setPassword(mail.smtpPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.timeout", "20000");
        properties.put("mail.smtp.connectiontimeout", "20000");
        properties.put("mail.smtp.writetimeout", "20000");

        String security = mail.smtpSecurity() == null ? "" : mail.smtpSecurity().trim().toUpperCase(Locale.ROOT);
        if ("SSL".equals(security)) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "true");
            if ("TLS".equals(security)) {
                properties.put("mail.smtp.starttls.required", "true");
            }
        }

        return sender;
    }

    private String appendSignature(String body, String signature) {
        String trimmedSignature = signature == null ? "" : signature.trim();
        if (trimmedSignature.isBlank()) {
            return body;
        }
        return body + System.lineSeparator() + System.lineSeparator() + trimmedSignature;
    }

    private List<String> buildNextSteps() {
        return List.of(
                "24 小时后查看打开率和回复率。",
                "对未回复客户安排第二轮跟进。",
                "把发送批次同步到 CRM，避免重复触达。"
        );
    }

    private String compactMailError(MailException exception) {
        String message = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        if (message == null || message.isBlank()) {
            return "邮件服务返回未知错误";
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    private String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value.trim();
    }
}
