package com.fixconnect.service;

import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends transactional e-mails over SMTP (Gmail, Outlook, SendGrid SMTP, Mailtrap ...).
 * Configure with MAIL_USERNAME / MAIL_PASSWORD (and MAIL_HOST / MAIL_PORT for non-Gmail providers).
 * If SMTP is not configured the e-mail is not sent and the link is written to the log instead,
 * so the flow can still be tested locally.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /** Support / admin contact shown in e-mails, invoices and messages. */
    public static final String SUPPORT_EMAIL = "adminfixconnectai@gmail.com";

    private final JavaMailSender mailSender;
    private final String username;
    private final String from;
    private final String fromName;
    /** Optional Brevo API key. When set, mail goes out over HTTPS (port 443) instead of SMTP - needed on hosts
     *  that block SMTP ports, such as Render's free plan. */
    private final String brevoApiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${fixconnect.mail.from:no-reply@fixconnect.ai}") String from,
                        @Value("${fixconnect.mail.from-name:FixConnect AI}") String fromName,
                        @Value("${fixconnect.mail.brevo-api-key:}") String brevoApiKey) {
        this.mailSender = mailSender;
        this.username = username;
        this.from = from;
        this.fromName = fromName;
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        if (usesBrevo()) {
            log.info("E-mails are sent through the Brevo HTTPS API as {}", from);
        } else if (!isConfigured()) {
            log.warn("SMTP is not configured (MAIL_USERNAME / MAIL_PASSWORD). E-mails will NOT be sent; "
                    + "password-reset links are printed to this log instead.");
        }
    }

    public boolean isConfigured() {
        return usesBrevo() || (username != null && !username.isBlank());
    }

    private boolean usesBrevo() {
        return !brevoApiKey.isEmpty();
    }

    @Async
    public void sendPasswordResetLink(String to, String name, String link, int validMinutes) {
        String subject = "Reset your FixConnect AI password";
        String text = "Hi " + name + ",\n\n"
                + "We received a request to reset the password for your FixConnect AI account.\n"
                + "Open this link to choose a new password (valid for " + validMinutes + " minutes):\n\n"
                + link + "\n\n"
                + "If you did not request this, you can ignore this e-mail - your password will not change.\n\n"
                + "- FixConnect AI Support";
        String html = layout("Reset your password",
                "<p>Hi " + esc(name) + ",</p>"
                        + "<p>We received a request to reset the password for your FixConnect AI account. "
                        + "Click the button below to choose a new password.</p>"
                        + button(link, "Reset Password")
                        + "<p style=\"color:#64748b;font-size:13px;\">This link expires in <strong>" + validMinutes
                        + " minutes</strong> and can be used only once.</p>"
                        + "<p style=\"color:#64748b;font-size:13px;\">Button not working? Copy this link into your browser:<br>"
                        + "<a href=\"" + esc(link) + "\" style=\"color:#1d4ed8;word-break:break-all;\">" + esc(link) + "</a></p>"
                        + "<p style=\"color:#64748b;font-size:13px;\">If you did not request a password reset, ignore this e-mail - "
                        + "your password stays the same.</p>");
        send(to, subject, text, html, link);
    }

    @Async
    public void sendPasswordChanged(String to, String name, String loginLink) {
        String subject = "Your FixConnect AI password was changed";
        String text = "Hi " + name + ",\n\nThe password for your FixConnect AI account was just changed.\n"
                + "If this wasn't you, reset your password immediately and contact " + SUPPORT_EMAIL + ".\n\n"
                + "Log in: " + loginLink + "\n\n- FixConnect AI Support";
        String html = layout("Password changed",
                "<p>Hi " + esc(name) + ",</p>"
                        + "<p>The password for your FixConnect AI account was just changed.</p>"
                        + button(loginLink, "Log in")
                        + "<p style=\"color:#b91c1c;font-size:13px;\">If this wasn't you, reset your password immediately and "
                        + "contact <a href=\"mailto:" + SUPPORT_EMAIL + "\">" + SUPPORT_EMAIL + "</a>.</p>");
        send(to, subject, text, html, null);
    }

    /** Account status / verification notices from the admin console. {@code htmlMessage} is trusted HTML. */
    @Async
    public void sendAccountNotice(String to, String name, String subject, String htmlMessage) {
        sendAccountNotice(to, name, subject, htmlMessage, null, null);
    }

    /** Same as above, with a button (e.g. "Log in") that opens {@code link}. */
    @Async
    public void sendAccountNotice(String to, String name, String subject, String htmlMessage, String link, String linkLabel) {
        String text = "Hi " + name + ",\n\n" + htmlMessage.replaceAll("<[^>]+>", "")
                + (link != null ? "\n\n" + linkLabel + ": " + link : "") + "\n\n- FixConnect AI Support";
        String html = layout(subject, "<p>Hi " + esc(name) + ",</p><p>" + htmlMessage + "</p>"
                + (link != null ? button(link, linkLabel) : ""));
        send(to, subject, text, html, link);
    }

    /** Tells the admin a new service provider signed up and is waiting for verification. */
    @Async
    public void sendNewProviderAlert(String to, String providerName, String providerEmail, String phone,
                                     String category, String serviceArea, String signupMethod, String reviewLink) {
        String subject = "New service provider awaiting verification: " + providerName;
        String text = "Hi Admin,\n\nA new service provider has registered on FixConnect AI and is waiting for verification.\n\n"
                + "Name: " + providerName + "\nE-mail: " + providerEmail + "\nPhone: " + orDash(phone)
                + "\nCategory: " + orDash(category) + "\nService area: " + orDash(serviceArea) + "\nSigned up with: " + signupMethod
                + "\n\nReview the ID proof and approve or reject the profile here:\n" + reviewLink + "\n\n- FixConnect AI";
        String row = "<tr><td style=\"padding:6px 12px 6px 0;color:#64748b;white-space:nowrap;\">%s</td>"
                + "<td style=\"padding:6px 0;font-weight:600;\">%s</td></tr>";
        String html = layout("New provider awaiting verification",
                "<p>Hi Admin,</p><p>A new service provider has registered and is waiting for your verification.</p>"
                        + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;margin:8px 0 4px;\">"
                        + String.format(row, "Name", esc(providerName))
                        + String.format(row, "E-mail", esc(providerEmail))
                        + String.format(row, "Phone", esc(orDash(phone)))
                        + String.format(row, "Category", esc(orDash(category)))
                        + String.format(row, "Service area", esc(orDash(serviceArea)))
                        + String.format(row, "Signed up with", esc(signupMethod))
                        + "</table>"
                        + button(reviewLink, "Review & Verify")
                        + "<p style=\"color:#64748b;font-size:13px;\">The provider stays offline until you approve the profile. "
                        + "Button not working? Open:<br><a href=\"" + esc(reviewLink) + "\" style=\"color:#1d4ed8;word-break:break-all;\">"
                        + esc(reviewLink) + "</a></p>");
        send(to, subject, text, html, reviewLink);
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    // ---------------------------------------------------------------------------------------------

    private void send(String to, String subject, String text, String html, String linkForLog) {
        if (!isConfigured()) {
            log.info("[MAIL NOT SENT - SMTP not configured] To: {} | Subject: {}{}", to, subject,
                    linkForLog != null ? " | Link: " + linkForLog : "");
            return;
        }
        if (usesBrevo()) {
            sendViaBrevo(to, subject, text, html);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(from, fromName, "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
            log.info("E-mail '{}' sent to {}", subject, to);
        } catch (Exception e) {
            log.error("Failed to send e-mail '{}' to {}: {}", subject, to, e.getMessage());
        }
    }

    /** Brevo transactional e-mail API: https://developers.brevo.com/reference/sendtransacemail */
    private void sendViaBrevo(String to, String subject, String text, String html) {
        String body = "{\"sender\":{\"name\":" + json(fromName) + ",\"email\":" + json(from) + "},"
                + "\"to\":[{\"email\":" + json(to) + "}],"
                + "\"subject\":" + json(subject) + ","
                + "\"htmlContent\":" + json(html) + ","
                + "\"textContent\":" + json(text) + "}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", brevoApiKey)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.info("E-mail '{}' sent to {} (Brevo)", subject, to);
            } else {
                log.error("Brevo rejected e-mail '{}' to {}: HTTP {} {}", subject, to, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send e-mail '{}' to {} via Brevo: {}", subject, to, e.getMessage());
        }
    }

    /** Minimal JSON string encoder (quotes included). */
    private static String json(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder(s.length() + 16).append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    private static String layout(String title, String body) {
        return "<!doctype html><html><body style=\"margin:0;background:#f1f5f9;font-family:Inter,Segoe UI,Arial,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:32px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;background:#ffffff;border-radius:16px;overflow:hidden;\">"
                + "<tr><td style=\"background:#0b1b3f;padding:20px 28px;color:#ffffff;font-size:20px;font-weight:800;\">FixConnect AI</td></tr>"
                + "<tr><td style=\"padding:28px;color:#0f172a;font-size:15px;line-height:1.6;\">"
                + "<h1 style=\"margin:0 0 12px;font-size:22px;color:#0b1b3f;\">" + esc(title) + "</h1>" + body
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px;background:#f8fafc;color:#94a3b8;font-size:12px;\">"
                + "FixConnect AI · Hyderabad, India · " + SUPPORT_EMAIL + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String button(String href, String label) {
        return "<p style=\"margin:24px 0;\"><a href=\"" + esc(href) + "\" style=\"background:#1d4ed8;color:#ffffff;"
                + "text-decoration:none;padding:12px 26px;border-radius:999px;font-weight:700;display:inline-block;\">"
                + esc(label) + "</a></p>";
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
