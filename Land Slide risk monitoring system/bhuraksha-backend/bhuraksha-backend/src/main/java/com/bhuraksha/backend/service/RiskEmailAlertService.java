package com.bhuraksha.backend.service;

import com.bhuraksha.backend.model.RiskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends a notification when a risk check returns MODERATE or HIGH.
 *
 * SMTP credentials are read from environment variables, never source control.
 * A zero cooldown delivers an alert for each explicit user check; a positive
 * cooldown is in-memory for this prototype.
 */
@Service
public class RiskEmailAlertService {

    public static final String SENT = "SENT";
    public static final String COOLDOWN = "COOLDOWN";
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String NOT_REQUESTED = "NOT_REQUESTED";
    public static final String FAILED = "FAILED";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    private static final Logger logger = LoggerFactory.getLogger(RiskEmailAlertService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("dd MMM uuuu, HH:mm z")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final JavaMailSender mailSender;
    private final Map<String, Instant> lastSentByRisk = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final String recipient;
    private final String sender;
    private final String mailHost;
    private final String mailUsername;
    private final String mailPassword;
    private final Duration cooldown;

    public RiskEmailAlertService(
            JavaMailSender mailSender,
            @Value("${alerts.email.enabled:false}") boolean enabled,
            @Value("${alerts.email.recipient:}") String recipient,
            @Value("${alerts.email.sender:}") String sender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${alerts.email.cooldown-seconds:0}") long cooldownSeconds) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.recipient = recipient;
        this.sender = sender;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.cooldown = Duration.ofSeconds(Math.max(0, cooldownSeconds));
    }

    /**
     * Attempts one SMTP delivery for a Moderate/High response. A successful
     * handoff is rate limited per place and risk level to prevent repeated
     * checks from flooding the configured inbox.
     */
    public synchronized String sendAlertIfNeeded(RiskResponse risk) {
        if (!"MODERATE".equals(risk.getRiskLevel()) && !"HIGH".equals(risk.getRiskLevel())) {
            return NOT_APPLICABLE;
        }
        if (!enabled || recipient.isBlank() || mailHost.isBlank()
                || mailUsername.isBlank() || mailPassword.isBlank()) {
            return NOT_CONFIGURED;
        }

        String riskKey = risk.getPlace() + ":" + risk.getRiskLevel();
        Instant now = Instant.now();
        Instant lastSent = lastSentByRisk.get(riskKey);
        if (!cooldown.isZero() && lastSent != null
                && Duration.between(lastSent, now).compareTo(cooldown) < 0) {
            return COOLDOWN;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        if (!sender.isBlank()) {
            message.setFrom(sender);
        }
        message.setSubject("BhuRaksha " + risk.getRiskLevel() + " landslide risk — " + risk.getPlace());
        message.setText(buildMessage(risk, now));

        try {
            mailSender.send(message);
            lastSentByRisk.put(riskKey, now);
            logger.info("Risk email alert sent for {} ({})", risk.getPlace(), risk.getRiskLevel());
            return SENT;
        } catch (MailException exception) {
            logger.warn("Risk email alert could not be sent for {}: {}", risk.getPlace(), exception.getMessage());
            return FAILED;
        }
    }

    private String buildMessage(RiskResponse risk, Instant createdAt) {
        return "BhuRaksha landslide risk alert\n\n"
                + "Risk level: " + risk.getRiskLevel() + "\n"
                + "Location: " + risk.getPlace() + " (" + risk.getDistrict() + ")\n"
                + "Risk score: " + risk.getRiskScore() + "/100\n"
                + "Season: " + risk.getSeason() + "\n"
                + "Recent rainfall reference: " + risk.getRecentRainfallMm() + " mm\n"
                + "Slope range: " + risk.getSlopeRange() + "\n"
                + "Assessment time: " + TIME_FORMAT.format(createdAt) + "\n\n"
                + "Guidance: " + risk.getAdvisory() + "\n\n"
                + "This is a BhuRaksha prototype notification. Follow official District Administration / DDMA alerts and evacuation instructions.";
    }
}
