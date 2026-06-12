package com.gulfnet.restaurantmanagement.job;

import com.gulfnet.shared_library.entity.Menu;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RestaurantMenuScheduleJob implements Job {

    private static final class MenuLiveEmailHtml {
        static final String TD_CLOSE = "</td>";
        static final String TR_CLOSE = "</tr>";
        static final String TABLE_CLOSE = "</table>";
        static final String DIV_CLOSE = "</div>";

        private MenuLiveEmailHtml() {
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Executes the scheduled restaurant menu activation job.
     * Updates restaurant menu mappings to LIVE status and sends email plus manager push/in-app notifications.
     *
     * @param context the Quartz job execution context containing menu ID and restaurant IDs
     * @throws JobExecutionException if the job execution fails
     */
    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Get job data
            UUID menuId = UUID.fromString(context.getJobDetail().getJobDataMap().getString("menuId"));
            String restaurantIdsStr = context.getJobDetail().getJobDataMap().getString("restaurantIds");
            
            log.info("=== EXECUTING SCHEDULED RESTAURANT MENU JOB ===");
            log.info("Job execution time: {}", LocalDateTime.now(ZoneOffset.UTC));
            log.info("Scheduled fire time: {}", context.getScheduledFireTime());
            log.info("Actual fire time: {}", context.getFireTime());
            log.info("Next fire time: {}", context.getNextFireTime());
            log.info("Executing scheduled restaurant menu status change job for menu {} and restaurants {}", 
                    menuId, restaurantIdsStr);

            // Get required beans from Spring context
            MenuRepository menuRepository = applicationContext.getBean(MenuRepository.class);
            RestaurantMenuMappingRepository restaurantMenuMappingRepository = applicationContext.getBean(RestaurantMenuMappingRepository.class);
            EmailSender emailSender = applicationContext.getBean(EmailSender.class);

                       // Find the menu and eagerly fetch translations to avoid LazyInitializationException
                       Menu menu = menuRepository.findById(menuId)
                       .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId));
               
               // Eagerly load translations to avoid LazyInitializationException in async method
               menu.getTranslations().size(); // This forces the lazy collection to load

            // Parse restaurant IDs
            String[] restaurantIdArray = restaurantIdsStr.split(",");
            Set<UUID> restaurantIds = new HashSet<>();
            for (String id : restaurantIdArray) {
                restaurantIds.add(UUID.fromString(id.trim()));
            }

            // Update restaurant menu mappings to LIVE status
            List<RestaurantMenuMapping> mappings = restaurantMenuMappingRepository.findById_RestaurantIdIn(
                    restaurantIds.stream().toList());
            
            for (RestaurantMenuMapping mapping : mappings) {
                if (mapping.getId().getMenuId().equals(menuId)) {
                    mapping.setStatus(RestaurantMenuMappingStatus.LIVE);
                    restaurantMenuMappingRepository.save(mapping);
                    log.info("Updated restaurant {} menu mapping to LIVE status", mapping.getId().getRestaurantId());
                }
            }

            // Email + FCM/WebSocket/in-app list for active managers (scheduled go-live path)
            sendRestaurantMenuLiveNotification(menu, restaurantIds, emailSender, applicationContext, Locale.ENGLISH);

            log.info("Restaurant menu status change job completed successfully for menu {} at {}", 
                    menuId, LocalDateTime.now(ZoneOffset.UTC));
            log.info("=== SCHEDULED RESTAURANT MENU JOB COMPLETED ===");

        } catch (Exception e) {
            log.error("Error executing scheduled restaurant menu status change job", e);
            throw new JobExecutionException("Failed to execute scheduled restaurant menu status change job", e);
        }
    }
    
    /**
     * Sends menu-live notifications using default English for template fallback when no locale is provided.
     *
     * @see #sendRestaurantMenuLiveNotification(Menu, Set, EmailSender, ApplicationContext, Locale)
     */
    @Async
    public static void sendRestaurantMenuLiveNotification(Menu menu, Set<UUID> restaurantIds, EmailSender emailSender, ApplicationContext applicationContext) {
        sendRestaurantMenuLiveNotification(menu, restaurantIds, emailSender, applicationContext, Locale.ENGLISH);
    }

    /**
     * Sends email and manager push (FCM/WebSocket) plus persisted in-app notifications when a menu goes live
     * for the given restaurants. Used for both scheduled Quartz activation and immediate LIVE updates.
     *
     * @param menu                 the menu entity that went live
     * @param restaurantIds        restaurants where the menu is live
     * @param emailSender          email sender
     * @param applicationContext   Spring context for beans
     * @param notificationLocale   fallback locale for managers without {@code languageCode}
     */
    @Async
    public static void sendRestaurantMenuLiveNotification(Menu menu, Set<UUID> restaurantIds, EmailSender emailSender, ApplicationContext applicationContext, Locale notificationLocale) {
        try {
            // Get required repositories from Spring context
            UserRepository userRepository = applicationContext.getBean(UserRepository.class);
            RoleRepository roleRepository = applicationContext.getBean(RoleRepository.class);
            RestaurantRepository restaurantRepository = applicationContext.getBean(RestaurantRepository.class);
            NotificationService notificationService = applicationContext.getBean(NotificationService.class);
            
            // Find MANAGER role
            Optional<Role> managerRole = roleRepository.findByName("MANAGER");
            if (managerRole.isEmpty()) {
                log.error("MANAGER role not found in the database");
                return;
            }
            UUID managerRoleId = managerRole.get().getId();
            Locale fallbackLocale = notificationLocale != null ? notificationLocale : Locale.ENGLISH;

            for (UUID restaurantId : restaurantIds) {
                try {
                    restaurantRepository.findById(restaurantId).ifPresent(restaurant -> {
                        try {
                            restaurant.getTranslations().size();
                        } catch (Exception e) {
                            log.debug("Could not preload restaurant translations: {}", e.getMessage());
                        }
                        List<User> activeManagers = userRepository
                                .findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId)
                                .stream()
                                .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                                .collect(Collectors.toList());
                        if (!activeManagers.isEmpty()) {
                            log.info("[MENU_LIVE][FCM] invoking notifyMenuLiveAtRestaurant menuId={} restaurantId={} activeManagerCount={} fallbackLocale={}",
                                    menu.getId(), restaurantId, activeManagers.size(), fallbackLocale);
                            notificationService.notifyMenuLiveAtRestaurant(menu, restaurant, activeManagers, fallbackLocale);
                            log.info("[MENU_LIVE][FCM] notifyMenuLiveAtRestaurant returned menuId={} restaurantId={}",
                                    menu.getId(), restaurantId);
                        } else {
                            log.info("[MENU_LIVE][FCM] skip restaurantId={} — no active managers for menuId={}", restaurantId, menu.getId());
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed menu-live push/in-app notification for restaurant {}: {}", restaurantId, e.getMessage(), e);
                }
            }

            // Collect all unique managers for the specified restaurants (email requires an address)
            Set<User> managersToNotify = new HashSet<>();
            
            for (UUID restaurantId : restaurantIds) {
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                managers.stream()
                        .filter(manager -> manager.getStatus() == EntityStatus.ACTIVE)
                        .filter(manager -> manager.getEmail() != null && !manager.getEmail().isEmpty())
                        .forEach(managersToNotify::add);
            }
            
            // Prepare email content
            String menuName = menu.getTranslations().isEmpty() ? null : menu.getTranslations().get(0).getName();

            MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
            
            // Send email to all managers
            int successCount = 0;
            int failureCount = 0;
            
            for (User manager : managersToNotify) {
                Locale userLocale = resolveUserLocale(manager);
                String localizedMenuName = menuName;

                String subject = messageUtil.getMessage("email.menu.live.subject", userLocale, localizedMenuName);
                String body = buildMenuLiveEmailHtml(
                        messageUtil,
                        userLocale,
                        localizedMenuName,
                        String.valueOf(menu.getVersion()));

                String managerEmail = manager.getEmail();
                if (trySendMenuLiveEmail(emailSender, managerEmail, subject, body)) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
            
            log.info("Restaurant menu live notifications sent to {} managers ({} success, {} failures) for menu {}", 
                    managersToNotify.size(), successCount, failureCount, menu.getId());
            
        } catch (Exception e) {
            log.error("Failed to send restaurant menu live notifications", e);
        }
    }

    private static boolean trySendMenuLiveEmail(EmailSender emailSender, String managerEmail, String subject, String body) {
        try {
            emailSender.sendEmail(managerEmail, subject, body);
            log.info("Restaurant menu live notification sent to manager: {}", managerEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to manager {}: {}", managerEmail, e.getMessage());
            return false;
        }
    }

    private static Locale resolveUserLocale(User user) {
        if (user != null && user.getLanguageCode() != null && !user.getLanguageCode().trim().isEmpty()) {
            return Locale.forLanguageTag(user.getLanguageCode().trim());
        }
        return Locale.ENGLISH;
    }

    /**
     * Builds the HTML email body used to notify managers that a menu is now live.
     * <p>
     * All dynamic values are HTML-escaped before insertion. Localized strings (including {@code email.menu.live.kds.notice})
     * are resolved via {@link MessageUtil} using the provided {@code userLocale}.
     * </p>
     *
     * @param messageUtil message resolver for localized strings
     * @param userLocale  recipient locale
     * @param menuName    menu display name (will be HTML-escaped)
     * @param version     menu version (will be HTML-escaped)
     * @return HTML email body
     */
    private static String buildMenuLiveEmailHtml(
            MessageUtil messageUtil,
            Locale userLocale,
            String menuName,
            String version) {
        String safeMenuName = escapeHtml(menuName);
        String safeVersion = escapeHtml(version);

        String titleText = messageUtil.getMessage("email.menu.live.title", userLocale);
        String detailsTitle = messageUtil.getMessage("email.menu.live.details.title", userLocale);
        String menuNameLabel = messageUtil.getMessage("email.menu.live.menu.name.label", userLocale);
        String versionLabel = messageUtil.getMessage("email.menu.live.menu.version.label", userLocale);
        String messageText = messageUtil.getMessage("email.menu.live.message", userLocale);
        String kdsNoticeText = messageUtil.getMessage("email.menu.live.kds.notice", userLocale);
        String bestRegards = messageUtil.getMessage("email.receipt.regards", userLocale);
        String companyName = messageUtil.getMessage("email.common.restaurant.management.system.name", userLocale);

        String safeTitleText = escapeHtml(titleText);
        String safeDetailsTitle = escapeHtml(detailsTitle);
        String safeMenuNameLabel = escapeHtml(menuNameLabel);
        String safeVersionLabel = escapeHtml(versionLabel);
        String safeMessageText = escapeHtml(messageText);
        String safeKdsNoticeText = escapeHtml(kdsNoticeText);
        String safeBestRegards = escapeHtml(bestRegards);
        String safeCompanyName = escapeHtml(companyName);

        return ""
                + "<!DOCTYPE html>"
                + "<html>"
                + "<body style=\"margin:0;padding:16px 0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">"
                + "<tr>"
                + "<td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:14px;"
                + "border:1px solid #e5e7eb;overflow:hidden;\">"
                + "<tr>"
                + "<td style=\"background:#2563eb;height:10px;\">&nbsp;</td>"
                + MenuLiveEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:20px 24px 8px 24px;\">"
                + "<div style=\"font-size:18px;color:#111827;font-weight:700;line-height:24px;\">"
                + safeTitleText
                + MenuLiveEmailHtml.DIV_CLOSE
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding: 0 24px 16px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;\">"
                + "<tr>"
                + "<td style=\"padding:14px 16px 8px 16px;font-size:14px;font-weight:700;color:#111827;\">"
                + safeDetailsTitle
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding: 0 16px 16px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr>"
                + "<td style=\"font-size:14px;color:#6b7280;padding:6px 0;\">"
                + safeMenuNameLabel
                + MenuLiveEmailHtml.TD_CLOSE
                + "<td align=\"right\" style=\"font-size:14px;color:#111827;font-weight:700;padding:6px 0;\">"
                + safeMenuName
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"font-size:14px;color:#6b7280;padding:6px 0;\">"
                + safeVersionLabel
                + MenuLiveEmailHtml.TD_CLOSE
                + "<td align=\"right\" style=\"font-size:14px;color:#111827;font-weight:700;padding:6px 0;\">"
                + safeVersion
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + MenuLiveEmailHtml.TABLE_CLOSE
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + MenuLiveEmailHtml.TABLE_CLOSE
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding: 0 24px 22px 24px;\">"
                + "<div style=\"font-size:14px;color:#4b5563;line-height:22px;\">"
                + safeMessageText
                + MenuLiveEmailHtml.DIV_CLOSE
                + "<div style=\"font-size:14px;color:#4b5563;line-height:22px;margin-top:12px;\">"
                + safeKdsNoticeText
                + MenuLiveEmailHtml.DIV_CLOSE
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:0 24px 24px 24px;\">"
                + "<div style=\"font-size:13px;color:#6b7280;line-height:20px;\">"
                + safeBestRegards
                + "<br/>"
                + safeCompanyName
                + MenuLiveEmailHtml.DIV_CLOSE
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + MenuLiveEmailHtml.TABLE_CLOSE
                + MenuLiveEmailHtml.TD_CLOSE
                + MenuLiveEmailHtml.TR_CLOSE
                + MenuLiveEmailHtml.TABLE_CLOSE
                + "</body>"
                + "</html>";
    }

    private static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
