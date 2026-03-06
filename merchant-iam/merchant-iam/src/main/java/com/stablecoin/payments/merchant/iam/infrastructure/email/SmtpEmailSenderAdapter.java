package com.stablecoin.payments.merchant.iam.infrastructure.email;

import com.stablecoin.payments.merchant.iam.domain.EmailSenderProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailSenderAdapter implements EmailSenderProvider {

    private static final DateTimeFormatter EXPIRY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    @Override
    public void sendInvitationEmail(String email, String fullName, String merchantName,
                                    String invitationToken, Instant expiresAt) {
        var acceptUrl = emailProperties.invitationBaseUrl() + "?token=" + invitationToken;
        var expiryFormatted = EXPIRY_FORMATTER.format(expiresAt);

        var message = new SimpleMailMessage();
        message.setFrom(emailProperties.from());
        message.setTo(email);
        message.setSubject("You've been invited to join " + merchantName + " on StableBridge");
        message.setText("""
                Hi %s,

                You've been invited to join %s on the StableBridge platform.

                Accept your invitation before it expires on %s:
                %s

                If you did not expect this invitation, you can safely ignore this email.

                The StableBridge Team
                """.formatted(fullName, merchantName, expiryFormatted, acceptUrl));

        log.info("Sending invitation email to={} merchant={}", email, merchantName);
        mailSender.send(message);
        log.debug("Invitation email sent to={}", email);
    }
}
