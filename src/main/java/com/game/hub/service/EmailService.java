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
    private final String fromAddress;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.email.mode:log}") String mode,
                        @Value("${app.email.from:no-reply@caro.local}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.mode = mode == null ? "log" : mode.trim();
        this.fromAddress = (fromAddress == null || fromAddress.isBlank()) ? "no-reply@caro.local" : fromAddress.trim();
    }

    public void sendEmail(String to, String subject, String body) {
        if (!"smtp".equalsIgnoreCase(mode)) {
            log.info("[EMAIL][{}] to={} subject={} body={}", mode.isBlank() ? "log" : mode, to, subject, body);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP mode enabled but JavaMailSender is not configured");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
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
}
