package com.game.hub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mode;
    private final String configuredFromAddress;
    private final String smtpHost;
    private final String smtpUsername;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.email.mode:auto}") String mode,
                        @Value("${app.email.from:no-reply@caro.local}") String fromAddress,
                        @Value("${spring.mail.host:}") String smtpHost,
                        @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSenderProvider = mailSenderProvider;
        this.mode = mode == null ? "log" : mode.trim();
        this.configuredFromAddress = (fromAddress == null || fromAddress.isBlank()) ? "no-reply@caro.local" : fromAddress.trim();
        this.smtpHost = smtpHost == null ? "" : smtpHost.trim();
        this.smtpUsername = smtpUsername == null ? "" : smtpUsername.trim();
    }

    public void sendEmail(String to, String subject, String body) {
        String effectiveMode = resolveEffectiveMode();
        if (!"smtp".equalsIgnoreCase(effectiveMode)) {
            if ("auto".equalsIgnoreCase(mode)) {
                log.warn("[EMAIL][auto->log] SMTP not configured, email not sent. to={} subject={} body={}", to, subject, body);
            } else {
                log.info("[EMAIL][{}] to={} subject={} body={}", mode.isBlank() ? "log" : mode, to, subject, body);
            }
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP mode enabled but JavaMailSender is not configured");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(resolveFromAddress());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("[EMAIL][smtp] sent to={} subject={}", to, subject);
        } catch (MailException ex) {
            log.error("Failed to send email to {} via SMTP: {}", to, ex.getMessage());
            throw new IllegalStateException("Failed to send email", ex);
        }
    }

    private String resolveEffectiveMode() {
        if (mode == null || mode.isBlank()) {
            return "log";
        }
        if ("smtp".equalsIgnoreCase(mode) || "log".equalsIgnoreCase(mode)) {
            return mode;
        }
        if ("auto".equalsIgnoreCase(mode)) {
            return isSmtpConfigured() ? "smtp" : "log";
        }
        log.warn("Unsupported app.email.mode='{}', fallback to log", mode);
        return "log";
    }

    private boolean isSmtpConfigured() {
        return !smtpHost.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    private String resolveFromAddress() {
        String from = configuredFromAddress == null ? "" : configuredFromAddress.trim();
        boolean looksLikePlaceholder = from.isBlank()
            || from.endsWith("@example.local")
            || from.endsWith("@caro.local");
        if (looksLikePlaceholder && !smtpUsername.isBlank()) {
            return smtpUsername;
        }
        return from.isBlank() ? "no-reply@caro.local" : from;
    }
}
