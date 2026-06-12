package com.gulfnet.usermanagement.util;

import com.gulfnet.shared_library.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Shared HTML generator for registration emails.
 * Keeps the mobile/web branching and table-based layout consistent across services.
 */
@Component
@RequiredArgsConstructor
public class RegistrationEmailHtmlFormatter {

    private final MessageUtil messageUtil;

    private static final String HTML_BR = "<br/>";
    private static final String HTML_TD_CLOSE_NL = "</td>\n";
    private static final String HTML_TR_OPEN_NL = "<tr>\n";
    private static final String HTML_TR_CLOSE_NL = "</tr>\n";
    private static final String HTML_TD_TR_CLOSE_NL = "</td></tr>\n";

    private static boolean isJapanese(Locale locale) {
        return locale != null && "ja".equalsIgnoreCase(locale.getLanguage());
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String formatFullName(User user, Locale locale) {
        String first = safe(user != null ? user.getFirstName() : null).trim();
        String last = safe(user != null ? user.getLastName() : null).trim();
        if (isJapanese(locale)) {
            return (last + " " + first).trim();
        }
        return (first + " " + last).trim();
    }

    private static String formatGreetingName(User user, Locale locale) {
        // For Japanese, prefer last name for greeting.
        String first = safe(user != null ? user.getFirstName() : null).trim();
        String last = safe(user != null ? user.getLastName() : null).trim();
        if (isJapanese(locale)) {
            return !last.isEmpty() ? last : first;
        }
        return !first.isEmpty() ? first : last;
    }

    /**
     * English-style "Dear {name}," vs Japanese "{familyName}様"
     */
    private void appendRegistrationGreetingLine(StringBuilder sb, Locale userLocale, String greetingName) {
        String name = greetingName != null ? greetingName : "";
        if (isJapanese(userLocale)) {
            sb.append(name)
                    .append(messageUtil.getMessage("user.registration.email.greeting", userLocale))
                    .append("\n");
        } else {
            sb.append(messageUtil.getMessage("user.registration.email.greeting", userLocale))
                    .append(" ")
                    .append(name)
                    .append(",\n");
        }
    }

    private void appendRegistrationEmailGreetingTableCellFooter(StringBuilder sb) {
        sb.append(HTML_TD_CLOSE_NL)
                .append(HTML_TR_CLOSE_NL)
                .append("\n");
    }

    private void appendRegistrationEmailBodyParagraph14Open(StringBuilder sb) {
        sb.append(HTML_TR_OPEN_NL)
                .append("<td style=\"padding:0 24px 20px 24px;font-size:14px;color:#4b5563;line-height:20px;\">\n");
    }

    private void appendRegistrationEmailBodyParagraph14Close(StringBuilder sb) {
        sb.append(HTML_TD_CLOSE_NL)
                .append(HTML_TR_CLOSE_NL)
                .append("\n");
    }

    private void appendRegistrationEmailBodyMessageParagraphRow(StringBuilder sb, Locale userLocale, String messageKey) {
        appendRegistrationEmailBodyParagraph14Row(sb,
                b -> b.append(messageUtil.getMessage(messageKey, userLocale)).append("\n"));
    }

    private void appendRegistrationEmailBodyMessageParagraphRowWithArgs(
            StringBuilder sb, Locale userLocale, String messageKey, Object... args) {
        appendRegistrationEmailBodyParagraph14Row(sb,
                b -> b.append(messageUtil.getMessage(messageKey, userLocale, args)).append("\n"));
    }

    private void appendRegistrationEmailBodyParagraph14Row(StringBuilder sb, Consumer<StringBuilder> body) {
        appendRegistrationEmailBodyParagraph14Open(sb);
        body.accept(sb);
        appendRegistrationEmailBodyParagraph14Close(sb);
    }

    /**
     * Appends a styled table row that displays the generated registration password.
     *
     * @param sb                HTML under construction
     * @param userLocale        locale for the password label message
     * @param generatedPassword plain-text password to show
     */
    private void appendRegistrationPasswordBoxRow(StringBuilder sb, Locale userLocale, String generatedPassword) {
        sb.append(HTML_TR_OPEN_NL)
                .append("<td style=\"padding:0 24px 20px 24px;\">\n")
                .append("<div style=\"background:#f3f4f6;border:1px solid #e5e7eb;border-radius:8px;padding:16px;text-align:center;\">\n")
                .append("<p style=\"margin:0;font-size:12px;color:#6b7280;\">")
                .append(messageUtil.getMessage("user.registration.email.password.label", userLocale))
                .append("</p>\n")
                .append("<p style=\"margin:6px 0 0;font-size:18px;font-weight:bold;color:#111827;\">")
                .append(generatedPassword)
                .append("</p>\n")
                .append("</div>\n")
                .append(HTML_TD_CLOSE_NL)
                .append(HTML_TR_CLOSE_NL)
                .append("\n");
    }

    /**
     * Appends a centered login button row when the user is a web-app user and a non-blank login URL is provided.
     *
     * @param sb            HTML under construction
     * @param userLocale    locale for the button label message
     * @param isWebAppUser  whether the recipient should see the login CTA
     * @param loginUrl      destination URL for the button (may be null)
     */
    private void appendRegistrationLoginButtonRowIfApplicable(
            StringBuilder sb, Locale userLocale, boolean isWebAppUser, String loginUrl) {
        if (isWebAppUser && loginUrl != null && !loginUrl.isEmpty()) {
            sb.append("<tr><td align=\"center\" style=\"padding:0 24px 20px 24px;\">\n")
                    .append("<a href=\"").append(loginUrl)
                    .append("\" style=\"display:inline-block;padding:10px 20px;background:#ffc365;color:#000;text-decoration:none;font-weight:bold;border-radius:6px;\">")
                    .append(messageUtil.getMessage("user.registration.email.login.button", userLocale))
                    .append("</a>\n")
                    .append(HTML_TD_TR_CLOSE_NL)
                    .append("\n");
        }
    }

    private void appendRegistrationLoginButtonThenFooter(
            StringBuilder sb, Locale userLocale, boolean isWebAppUser, String loginUrl) {
        appendRegistrationLoginButtonRowIfApplicable(sb, userLocale, isWebAppUser, loginUrl);
        appendRegistrationEmailStandardFooterAndClose(sb, userLocale);
    }

    /**
     * Opens the standard registration email HTML shell through the greeting header cell (caller fills cell content).
     *
     * @param extraNewlineAfterPresentationTableOpen matches the self-service template which inserts an extra blank line
     *                                             after the inner {@code role="presentation"} table opens
     */
    private void appendRegistrationEmailDocumentShellToGreetingCell(StringBuilder sb, boolean extraNewlineAfterPresentationTableOpen) {
        sb.append("<!DOCTYPE html>\n")
                .append("<html>\n")
                .append("<body style=\"margin:0;padding:16px 0;background:#f9fafb;font-family:Arial,Helvetica,sans-serif;\">\n")
                .append("\n")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n")
                .append("<tr><td align=\"center\">\n")
                .append("\n")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                        + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:14px;"
                        + "border:1px solid #e5e7eb;overflow:hidden;\">\n")
                .append("\n");
        if (extraNewlineAfterPresentationTableOpen) {
            sb.append("\n");
        }
        sb.append("<tr><td style=\"background:#2563eb;height:10px;\">&nbsp;</td></tr>\n")
                .append(HTML_TR_OPEN_NL)
                .append("<td style=\"padding:20px 24px 8px 24px;font-size:16px;color:#111827;font-weight:bold;\">\n");
    }

    private void appendRegistrationEmailStandardFooterAndClose(StringBuilder sb, Locale userLocale) {
        sb.append(HTML_TR_OPEN_NL)
                .append("<td style=\"padding:0 24px 24px 24px;font-size:13px;color:#6b7280;\">\n")
                .append(messageUtil.getMessage("user.registration.email.regards", userLocale))
                .append("<br>")
                .append(messageUtil.getMessage("user.registration.email.team", userLocale))
                .append(HTML_TD_CLOSE_NL)
                .append(HTML_TR_CLOSE_NL)
                .append("\n");
        appendRegistrationEmailOuterShellClose(sb);
    }

    private void appendRegistrationEmailOuterShellClose(StringBuilder sb) {
        sb.append("</table>\n")
                .append("\n")
                .append(HTML_TD_TR_CLOSE_NL)
                .append("</table>\n")
                .append("\n")
                .append("</body>\n")
                .append("</html>");
    }

    /**
     * Builds a complete localized HTML document for a new-user registration email (table-based layout
     * for mail clients). Chooses among mobile self-service, mobile manager notification, web/HQ, and
     * related variants based on the boolean flags; see the 11-parameter overload for the full matrix.
     * <p>
     * Equivalent to
     * {@link #buildRegistrationEmailHtml(User, String, Locale, String, boolean, boolean, boolean, String, boolean, boolean, String)}
     * with {@code hqNotifyMobileStaffCredentials=false} and {@code recipientGreetingName=null}.
     *
     * @param user                 the registered user (name, user code, etc.)
     * @param generatedPassword    password to show in the email body
     * @param userLocale           locale for {@link MessageUtil} message resolution
     * @param userRoleName         localized role label (e.g. manager template)
     * @param isMobileAppUser      {@code true} to use mobile-oriented templates
     * @param isSendingToUserEmail when mobile, {@code true} if the message is addressed to the new user
     * @param isWebAppUser         whether a web login call-to-action may be included when {@code loginUrl} is set
     * @param loginUrl             optional first-login URL for a button in web-oriented templates
     * @param isDefaultEmailUsed   when web, {@code true} if the org default email path and copy apply
     * @return HTML string ({@code text/html}), including inline styles
     */
    public String buildRegistrationEmailHtml(
            User user,
            String generatedPassword,
            Locale userLocale,
            String userRoleName,
            boolean isMobileAppUser,
            boolean isSendingToUserEmail,
            boolean isWebAppUser,
            String loginUrl,
            boolean isDefaultEmailUsed
    ) {
        return buildRegistrationEmailHtml(
                user,
                generatedPassword,
                userLocale,
                userRoleName,
                isMobileAppUser,
                isSendingToUserEmail,
                isWebAppUser,
                loginUrl,
                isDefaultEmailUsed,
                false,
                null
        );
    }

    /**
     * @param hqNotifyMobileStaffCredentials when true, email explains that credentials are for a mobile staff account,
     *                                         addressed to the HQ admin ({@code recipientGreetingName}).
     * @param recipientGreetingName         HQ admin first name (or similar); falls back to localized "HQ Admin" if blank
     */
    public String buildRegistrationEmailHtml(
            User user,
            String generatedPassword,
            Locale userLocale,
            String userRoleName,
            boolean isMobileAppUser,
            boolean isSendingToUserEmail,
            boolean isWebAppUser,
            String loginUrl,
            boolean isDefaultEmailUsed,
            boolean hqNotifyMobileStaffCredentials,
            String recipientGreetingName
    ) {
        if (isMobileAppUser && isSendingToUserEmail) {
            String greetingName = formatGreetingName(user, userLocale);

            StringBuilder sb = new StringBuilder(1024);
            appendRegistrationEmailDocumentShellToGreetingCell(sb, true);
            appendRegistrationGreetingLine(sb, userLocale, greetingName);
            appendRegistrationEmailGreetingTableCellFooter(sb);
            appendRegistrationEmailBodyMessageParagraphRow(sb, userLocale, "user.registration.email.body");
            sb.append(HTML_TR_OPEN_NL)
                    .append("<td style=\"padding:0 24px 16px 24px;font-size:14px;color:#4b5563;line-height:20px;\">\n")
                    .append("<strong>")
                    .append(messageUtil.getMessage("user.registration.email.manager.usercode.label", userLocale))
                    .append(":</strong> ")
                    .append(user.getUserCode() != null ? user.getUserCode() : "")
                    .append("\n")
                    .append(HTML_TD_CLOSE_NL)
                    .append(HTML_TR_CLOSE_NL)
                    .append("\n");
            appendRegistrationPasswordBoxRow(sb, userLocale, generatedPassword);
            appendRegistrationEmailStandardFooterAndClose(sb, userLocale);

            return sb.toString();
        }

        if (isMobileAppUser) {
            String fullName = formatFullName(user, userLocale);
            String roleName = userRoleName != null ? userRoleName : "";
            String roleNameLower = !roleName.isBlank() ? roleName.toLowerCase() : roleName;

            StringBuilder sb = new StringBuilder(1024);
            appendRegistrationEmailDocumentShellToGreetingCell(sb, false);
            sb.append(messageUtil.getMessage("user.registration.email.manager.greeting", userLocale, fullName))
                    .append("\n");
            appendRegistrationEmailGreetingTableCellFooter(sb);
            appendRegistrationEmailBodyParagraph14Open(sb);
            sb.append("<div style=\"background:#f3f4f6;border:1px solid #e5e7eb;border-radius:8px;padding:16px;\">\n")
                    .append(messageUtil.getMessage("user.registration.email.manager.body.title", userLocale))
                    .append(HTML_BR)
                    .append(messageUtil.getMessage("user.registration.email.manager.name.label", userLocale))
                    .append(": ")
                    .append(fullName)
                    .append(HTML_BR)
                    .append(messageUtil.getMessage("user.registration.email.manager.role.label", userLocale))
                    .append(": ")
                    .append(roleName)
                    .append(HTML_BR)
                    .append(messageUtil.getMessage("user.registration.email.manager.usercode.label", userLocale))
                    .append(": ")
                    .append(user.getUserCode())
                    .append("</div>\n");
            appendRegistrationEmailBodyParagraph14Close(sb);
            appendRegistrationPasswordBoxRow(sb, userLocale, generatedPassword);
            sb.append(HTML_TR_OPEN_NL)
                    .append("<td style=\"padding:0 24px 16px 24px;font-size:13px;color:#6b7280;\">\n")
                    .append(messageUtil.getMessage("user.registration.email.manager.share.text", userLocale, roleNameLower))
                    .append(HTML_TD_CLOSE_NL)
                    .append(HTML_TR_CLOSE_NL)
                    .append("\n");
            appendRegistrationEmailStandardFooterAndClose(sb, userLocale);

            return sb.toString();
        }

        if (hqNotifyMobileStaffCredentials && !isMobileAppUser) {
            String greetName = (recipientGreetingName != null && !recipientGreetingName.isBlank())
                    ? recipientGreetingName
                    : messageUtil.getMessage("user.registration.email.admin.hq", userLocale);
            String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                    + (user.getLastName() != null ? " " + user.getLastName() : "");
            String role = userRoleName != null ? userRoleName : "";

            StringBuilder sb = new StringBuilder(1024);
            appendRegistrationEmailDocumentShellToGreetingCell(sb, false);
            appendRegistrationGreetingLine(sb, userLocale, greetName);
            appendRegistrationEmailGreetingTableCellFooter(sb);
            appendRegistrationEmailBodyMessageParagraphRowWithArgs(sb, userLocale, "user.registration.email.hq.mobile.body",
                    role, fullName, user.getUserCode());
            appendRegistrationPasswordBoxRow(sb, userLocale, generatedPassword);

            appendRegistrationLoginButtonThenFooter(sb, userLocale, isWebAppUser, loginUrl);

            return sb.toString();
        }

        // Web app (HQ_ADMIN, MANAGER)
        String greetingName = isDefaultEmailUsed
                ? messageUtil.getMessage("user.registration.email.admin.hq", userLocale)
                : formatGreetingName(user, userLocale);

        StringBuilder sb = new StringBuilder(1024);
        appendRegistrationEmailDocumentShellToGreetingCell(sb, false);
        appendRegistrationGreetingLine(sb, userLocale, greetingName);
        appendRegistrationEmailGreetingTableCellFooter(sb);
        appendRegistrationEmailBodyParagraph14Row(sb, s -> {
            if (isDefaultEmailUsed) {
                s.append(messageUtil.getMessage(
                                "user.registration.email.default.intro",
                                userLocale,
                                formatFullName(user, userLocale),
                                user.getUserCode()
                        ))
                        .append("\n");
            } else {
                s.append(messageUtil.getMessage("user.registration.email.body", userLocale))
                        .append("\n");
            }
        });
        appendRegistrationPasswordBoxRow(sb, userLocale, generatedPassword);

        if (isDefaultEmailUsed) {
            sb.append("<tr><td style=\"padding:0 24px 16px 24px;font-size:12px;color:#6b7280;\">\n")
                    .append("<p style=\"margin:0;\"><i>")
                    .append(messageUtil.getMessage("user.registration.email.default.notice", userLocale, user.getUserCode()))
                    .append("</i></p>\n")
                    .append(HTML_TD_TR_CLOSE_NL)
                    .append("\n");
        }

        appendRegistrationLoginButtonThenFooter(sb, userLocale, isWebAppUser, loginUrl);

        return sb.toString();
    }
}

