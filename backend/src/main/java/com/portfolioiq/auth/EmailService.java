package com.portfolioiq.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends email through SMTP when SPRING_MAIL_HOST is configured. Failures never break the calling request.
 * With app.mail.log-only=true (development only) the message body, including its one-time link, is logged instead.
 */
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final boolean logOnly;

    public EmailService(ObjectProvider<JavaMailSender> sender,
                        @Value("${app.mail.from}") String from,
                        @Value("${app.mail.log-only:false}") boolean logOnly) {
        this.sender = sender;
        this.from = from;
        this.logOnly = logOnly;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
            if (logOnly) {
                log.warn("[DEV ONLY - SMTP not configured] To: {} | Subject: {}\n{}", to, subject, body);
            } else {
                log.warn("SMTP is not configured; email '{}' was not sent", subject);
            }
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (MailException e) {
            log.error("Failed to send email '{}'", subject, e);
        }
    }
}
