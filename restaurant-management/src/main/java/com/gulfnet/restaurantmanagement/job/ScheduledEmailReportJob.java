package com.gulfnet.restaurantmanagement.job;

import com.gulfnet.shared_library.entity.EmailSchedule;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import com.gulfnet.shared_library.repository.EmailScheduleRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.exception.EmailReportException;
import com.gulfnet.restaurantmanagement.service.ReportsService;
import com.gulfnet.restaurantmanagement.service.ScheduleManagerService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Parameter record for report generation methods to reduce duplication
 */
record ReportGenerationParams(
    ReportsService reportsService,
    UUID restaurantId,
    UUID restaurantGroupId,
    String userId,
    String userRole,
    String period,
    LocalDateTime startDate,
    LocalDateTime endDate,
    String locale,
    Workbook workbook,
    EmailSchedule schedule
) {}

/** Restaurant and optional group context reused when building scheduled report workbooks. */
record ScheduledReportVenue(
        com.gulfnet.shared_library.entity.Restaurant restaurant,
        com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup) {}

@Slf4j
@Component
public class ScheduledEmailReportJob implements Job {
    // Role constants
    private static final String ROLE_HQ_ADMIN = "HQ_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";

    // File extension constants
    private static final String FILE_EXT_XLSX = ".xlsx";
    private static final String FILE_EXT_CSV = ".csv";

    private static final String msgReportsExportFilenameOverview = "reports.export.filename.overview";
    private static final String msgReportsExportFilenamePaymentAndFinancials = "reports.export.filename.paymentAndFinancials";
    private static final String msgReportsExportFilenamePerformance = "reports.export.filename.performance";
    private static final String msgReportsExportFilenameDailySummary = "reports.export.filename.dailySummary";

    // Report column header constants
    private static final String HEADER_TOTAL_SALES = "Total Sales";
    private static final String HEADER_TOTAL_ORDERS = "Total Orders";
    private static final String HEADER_AVG_ORDER_VALUE = "Average Order Value";
    private static final String HEADER_PAYMENT_METHOD = "Payment Method";
    private static final String HEADER_DATE_TIME = "Date & Time";
    private static final String HEADER_REASON = "Reason";
    private static final String REPORT_TITLE_SUFFIX = " REPORT";

    private static final class ScheduledReportEmailHtml {
        static final String TD_CLOSE = "</td>";
        static final String TR_CLOSE = "</tr>";
        static final String TABLE_CLOSE = "</table>";
        static final String DIV_CLOSE = "</div>";

        private ScheduledReportEmailHtml() {
        }
    }

    // Date/time formatter (reusable, thread-safe)
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    /**
     * Quartz entry point that generates and emails a scheduled report.
     * <p>
     * Reads {@code scheduleId} from the job data map, validates the schedule is present/active, resolves recipient
     * locale, generates the report (Excel) for the configured report type and date range, and sends an email with the
     * report attached.
     * </p>
     *
     * @param context Quartz execution context (must contain {@code scheduleId})
     * @throws JobExecutionException when execution fails in a way Quartz should treat as a job failure
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Get schedule ID from job data
            String scheduleIdStr = context.getJobDetail().getJobDataMap().getString("scheduleId");
            UUID scheduleId = UUID.fromString(scheduleIdStr);

            log.info("=== EXECUTING SCHEDULED EMAIL REPORT JOB ===");
            log.info("Job execution time: {}", LocalDateTime.now(ZoneOffset.UTC));
            log.info("Scheduled fire time: {}", context.getScheduledFireTime());
            log.info("Actual fire time: {}", context.getFireTime());
            log.info("Next fire time: {}", context.getNextFireTime());
            log.info("Executing scheduled email report job for schedule: {}", scheduleId);

            // Get required beans from Spring context
            EmailScheduleRepository emailScheduleRepository = applicationContext.getBean(EmailScheduleRepository.class);
            ReportsService reportsService = applicationContext.getBean(ReportsService.class);
            EmailSender emailSender = applicationContext.getBean(EmailSender.class);
            ScheduleManagerService scheduleManagerService = applicationContext.getBean(ScheduleManagerService.class);
            UserRepository userRepository = applicationContext.getBean(UserRepository.class);

            // Fetch schedule from database
            EmailSchedule schedule = emailScheduleRepository.findById(scheduleId)
                    .orElse(null);

            // If schedule is deleted or doesn't exist, skip execution
            if (schedule == null) {
                log.warn("Schedule {} not found (may have been deleted), skipping execution", scheduleId);
                // Note: The Quartz job should have been deleted when schedule was deleted,
                // but this is a safety check in case job deletion failed
                return;
            }

            // Validate schedule is still active
            if (!schedule.getIsActive()) {
                log.warn("Schedule {} is not active, skipping execution", scheduleId);
                return;
            }

            // Skip if recipient email is not registered in users table
            String recipientEmail = schedule.getRecipientEmail();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.warn("Schedule {} has no recipient email, skipping email send", scheduleId);
                return;
            }

            User recipient = userRepository.findByEmail(recipientEmail).orElse(null);
            if (recipient == null) {
                log.warn("Recipient email {} not found in users table for schedule {}, skipping email send",
                        recipientEmail, scheduleId);
                return;
            }

            Locale recipientLocale = resolveUserLocale(recipient);
            String locale = recipientLocale.getLanguage();

            // Generate report based on report type
            ReportType reportType = schedule.getReportType();
            UUID restaurantId = schedule.getRestaurant() != null ? schedule.getRestaurant().getId() : null;
            UUID restaurantGroupId = schedule.getRestaurantGroup() != null ? schedule.getRestaurantGroup().getId() : null;

            // Validate that either restaurantId or restaurantGroupId is provided
            if (restaurantId == null && restaurantGroupId == null) {
                log.error("Either Restaurant ID or Restaurant Group ID is required for report type: {}", reportType);
                throw new EmailReportException("Either Restaurant ID or Restaurant Group ID is required for this report type");
            }

            // Load creator explicitly to avoid lazy proxy access outside a transactional session.
            User creator = null;
            if (schedule.getCreatedBy() != null && schedule.getCreatedBy().getId() != null) {
                creator = userRepository.findById(schedule.getCreatedBy().getId()).orElse(null);
            }
            String userId = creator != null ? creator.getId().toString() : null;
            String userRole = creator != null && creator.getRoleId() != null
                    ? getRoleName(creator.getRoleId(), applicationContext) : ROLE_HQ_ADMIN; // Default to HQ_ADMIN if role not found

            log.info("Generating report - restaurantId: {}, restaurantGroupId: {}, userRole: {}", 
                    restaurantId, restaurantGroupId, userRole);

            // Calculate date range for previous period based on frequency
            LocalDateTime[] dateRange = calculatePreviousPeriodDateRange(schedule.getFrequency(), schedule.getScheduledDay());
            LocalDateTime startDate = dateRange[0];
            LocalDateTime endDate = dateRange[1];

            // Generate report file in memory (CSV for DAILY_SALES_SUMMARY, Excel for others)
            ByteArrayOutputStream reportOutputStream = new ByteArrayOutputStream();
            String filename;
            String contentType;
            byte[] reportData;

            if (reportType == ReportType.DAILY_SALES_SUMMARY) {
                // Generate CSV for daily summary reports
                filename = generateCsvReportAndGetFilename(
                        reportsService, restaurantId, restaurantGroupId,
                        userId, userRole, startDate, endDate, locale, reportOutputStream);
                contentType = "text/csv";
            } else {
                // Generate Excel for other report types
                filename = generateReportAndGetFilename(
                        reportsService, reportType, restaurantId, restaurantGroupId,
                        userId, userRole, null, startDate, endDate, locale, reportOutputStream, schedule);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            
            reportData = reportOutputStream.toByteArray();

            // Get schedule name from translations based on locale
            String scheduleName = getScheduleNameForLocale(schedule, locale, applicationContext);
            
            // Send to the schedule recipient (set from creator's email at schedule creation)
            sendEmailWithAttachment(
                    emailSender, recipientEmail,
                    scheduleName, reportType, filename,
                    reportData, contentType, locale, userRole);

            // Update schedule execution tracking
            schedule.setLastExecutedAt(OffsetDateTime.now(ZoneOffset.UTC));
            schedule.setNextExecutionAt(scheduleManagerService.calculateNextExecutionTime(schedule));
            emailScheduleRepository.save(schedule);

            log.info("Scheduled email report job completed successfully for schedule {} at {}",
                    scheduleId, LocalDateTime.now(ZoneOffset.UTC));
            log.info("=== SCHEDULED EMAIL REPORT JOB COMPLETED ===");

        } catch (Exception e) {
            log.error("Error executing scheduled email report job", e);
            throw new JobExecutionException("Failed to execute scheduled email report job", e);
        }
    }

    /**
     * Calculates the date range for the previous period based on frequency
     * Returns [startDate, endDate] in UTC timezone
     * All times are at midnight UTC (00:00:00 UTC)
     * 
     * - DAILY: Current day (today 00:00:00 UTC to 23:59:59.999 UTC)
     * - WEEKLY: Previous week based on scheduledDay
     *   - If scheduledDay = 1 (Sunday): Previous week from last Sunday 00:00:00 UTC to previous Saturday 23:59:59.999 UTC
     *   - Otherwise: Previous week from Monday 00:00:00 UTC to Sunday 23:59:59.999 UTC
     * - MONTHLY: Previous month (1st 00:00:00 UTC to last day 23:59:59.999 UTC)
     */
    private LocalDateTime[] calculatePreviousPeriodDateRange(ScheduleFrequency frequency, Integer scheduledDay) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime startDate;
        LocalDateTime endDate;

        switch (frequency) {
            case DAILY:
                // Current day: today 00:00:00 UTC to 23:59:59.999 UTC
                LocalDateTime today = now;
                startDate = today.toLocalDate().atStartOfDay().atZone(ZoneOffset.UTC).toLocalDateTime();
                endDate = today.toLocalDate().atTime(23, 59, 59, 999999999).atZone(ZoneOffset.UTC).toLocalDateTime();
                break;

            case WEEKLY:
                // Handle weekly based on scheduledDay
                // scheduledDay: 1=Sunday, 2=Monday, ..., 7=Saturday
                if (scheduledDay != null && scheduledDay == 1) {
                    // Sunday to Saturday week
                    // Find the most recent Sunday
                    int currentDayOfWeek = now.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
                    int daysToLastSunday;
                    if (currentDayOfWeek == 7) {
                        // Today is Sunday, go back 7 days to get last Sunday
                        daysToLastSunday = 7;
                    } else {
                        // Go back to last Sunday (currentDayOfWeek days + 1 to get to Sunday)
                        daysToLastSunday = currentDayOfWeek + 1;
                    }
                    LocalDateTime lastSunday = now.minusDays(daysToLastSunday).toLocalDate().atStartOfDay().atZone(ZoneOffset.UTC).toLocalDateTime();
                    startDate = lastSunday.minusDays(7); // Previous week's Sunday
                    endDate = startDate.plusDays(6).toLocalDate().atTime(23, 59, 59, 999999999).atZone(ZoneOffset.UTC).toLocalDateTime(); // Previous week's Saturday
                } else {
                    // Default: Monday to Sunday week
                    int currentDayOfWeek = now.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
                    int daysToLastMonday = (currentDayOfWeek == 1) ? 7 : currentDayOfWeek - 1;
                    LocalDateTime lastMonday = now.minusDays(daysToLastMonday).toLocalDate().atStartOfDay().atZone(ZoneOffset.UTC).toLocalDateTime();
                    startDate = lastMonday.minusDays(7); // Previous week's Monday
                    endDate = startDate.plusDays(6).toLocalDate().atTime(23, 59, 59, 999999999).atZone(ZoneOffset.UTC).toLocalDateTime(); // Previous week's Sunday
                }
                break;

            case MONTHLY:
                // Previous month: 1st 00:00:00 UTC to last day 23:59:59.999 UTC
                LocalDateTime firstOfCurrentMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay().atZone(ZoneOffset.UTC).toLocalDateTime();
                LocalDateTime lastOfPreviousMonth = firstOfCurrentMonth.minusNanos(1); // Last moment of previous month
                LocalDateTime firstOfPreviousMonth = lastOfPreviousMonth.toLocalDate().withDayOfMonth(1).atStartOfDay().atZone(ZoneOffset.UTC).toLocalDateTime();
                startDate = firstOfPreviousMonth;
                endDate = lastOfPreviousMonth;
                break;

            default:
                throw new IllegalArgumentException("Unsupported frequency: " + frequency);
        }

        log.info("Calculated previous period date range for {} (scheduledDay: {}): {} to {}", frequency, scheduledDay, startDate, endDate);
        return new LocalDateTime[]{startDate, endDate};
    }

    /**
     * Generates the scheduled report into an in-memory Excel workbook and writes it to {@code outputStream}.
     * <p>
     * Chooses a report generator based on {@code reportType}, writes the generated workbook to the provided stream,
     * and returns a timestamped filename for the email attachment.
     * </p>
     *
     * @param reportsService    reports service used to fetch report data
     * @param reportType        report type to generate
     * @param restaurantId      optional restaurant id scope
     * @param restaurantGroupId optional restaurant group id scope
     * @param userId            acting user id used for service authorization
     * @param userRole          acting user role used for service authorization
     * @param period            optional period string (used by some report APIs)
     * @param startDate         report start datetime (UTC)
     * @param endDate           report end datetime (UTC)
     * @param locale            locale/language tag string
     * @param outputStream      destination stream for the generated Excel bytes
     * @param schedule          schedule providing report context (title)
     * @return generated report filename (for attachment)
     * @throws IOException when workbook writing fails
     * @throws IllegalArgumentException when the report type is unsupported
     */
    private String generateReportAndGetFilename(
            ReportsService reportsService,
            ReportType reportType,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale,
            ByteArrayOutputStream outputStream,
            EmailSchedule schedule) throws IOException {

        MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        String filename;

        // Create a mock HttpServletResponse to capture Excel output
        // We'll use a custom approach to generate Excel in memory
        try (Workbook workbook = new XSSFWorkbook()) {
            switch (reportType) {
                case SALES:
                case SALES_AND_REVENUE:
                case DAILY_SALES_SUMMARY:
                    filename = buildExportFilename(messageUtil, msgReportsExportFilenameOverview, userLocale, FILE_EXT_XLSX);
                    // Call getReportsOverview internally and generate Excel
                    generateOverviewReportExcel(new ReportGenerationParams(reportsService, restaurantId, restaurantGroupId, 
                            userId, userRole, null, startDate, endDate, locale, workbook, schedule));
                    break;

                case PAYMENT_TYPES_BREAKDOWN:
                case PAYMENT_AND_FINANCIAL:
                    filename = buildExportFilename(messageUtil, msgReportsExportFilenamePaymentAndFinancials, userLocale, FILE_EXT_XLSX);
                    generatePaymentFinancialsReportExcel(new ReportGenerationParams(reportsService, restaurantId, restaurantGroupId, 
                            userId, userRole, period, startDate, endDate, locale, workbook, schedule));
                    break;

                case MENU_PERFORMANCE:
                case EMPLOYEE_PERFORMANCE:
                case STAFF_PERFORMANCE:
                    filename = buildExportFilename(messageUtil, msgReportsExportFilenamePerformance, userLocale, FILE_EXT_XLSX);
                    generatePerformanceReportExcel(new ReportGenerationParams(reportsService, restaurantId, restaurantGroupId, 
                            userId, userRole, period, startDate, endDate, locale, workbook, schedule));
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported report type: " + reportType);
            }

            workbook.write(outputStream);
        }

        return filename;
    }

    /**
     * Generates the “overview” report workbook content for a scheduled email.
     * <p>
     * Fetches overview data from {@link ReportsService#getReportsOverview} for the provided date range and writes the
     * same sheet structure used by the interactive export (summary/payment types plus optional itemized/table-wise/
     * discounts sheets) into the provided workbook.
     * </p>
     *
     * @param params report generation parameters (includes workbook, scope, date range, and schedule)
     * @throws IOException when writing to the workbook fails
     * @throws EmailReportException when report data cannot be retrieved
     */
    private void generateOverviewReportExcel(ReportGenerationParams params) throws IOException {
        
        log.info("Generating overview report Excel for restaurantId: {}, restaurantGroupId: {}, startDate: {}, endDate: {}", 
                params.restaurantId(), params.restaurantGroupId(), params.startDate(), params.endDate());
        
        // Call getReportsOverview internally to get the data
        com.gulfnet.shared_library.model.response.dto.ResponseDto<com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse> overviewResponse = 
                params.reportsService().getReportsOverview(
                        null, // period - not used when startDate/endDate are provided
                        params.startDate() != null ? params.startDate().atOffset(ZoneOffset.UTC) : null,
                        params.endDate() != null ? params.endDate().atOffset(ZoneOffset.UTC) : null,
                        params.restaurantId(),
                        params.restaurantGroupId(),
                        1, // itemizedPage
                        1000, // itemizedSize - get all items for export
                        null, // itemizedSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        1, // tableWisePage
                        1000, // tableWiseSize - get all items for export
                        null, // tableWiseSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        1, // discountsPage
                        1000, // discountsSize - get all items for export
                        null, // discountsSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        params.userId(),
                        params.userRole(),
                        params.locale());
        
        if (overviewResponse == null || overviewResponse.getData() == null) {
            throw new EmailReportException("Failed to get reports overview data");
        }
        
        com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse data = overviewResponse.getData();
        
        ScheduledReportVenue venue = resolveRestaurantAndGroupForSchedule(params);
        com.gulfnet.shared_library.entity.Restaurant restaurant = venue.restaurant();
        com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup = venue.restaurantGroup();
        
        // Generate Excel sheets using the same structure as exportReportsToExcel
        org.apache.poi.ss.usermodel.CellStyle headerStyle = createHeaderStyle(params.workbook());
        org.apache.poi.ss.usermodel.CellStyle titleStyle = createTitleStyle(params.workbook());
        String currencySymbol = getChainCurrencySymbol();
        org.apache.poi.ss.usermodel.CellStyle monetaryStyle = createMonetaryNumberStyle(params.workbook(), currencySymbol);
        org.apache.poi.ss.usermodel.CellStyle percentageDecimalStyle = createNumberStyle(params.workbook());
        
        MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
        Locale userLocale = Locale.forLanguageTag(params.locale() != null ? params.locale() : "en");

        // Sheet 1: Summary & Payment Types
        createSummarySheetForSchedule(params.workbook(), data.getDailySalesSummary(), data.getPaymentTypesBreakdown(),
                restaurant, restaurantGroup, params.startDate(), params.endDate(), headerStyle, titleStyle,
                currencySymbol, monetaryStyle, percentageDecimalStyle, params.schedule(), userLocale, messageUtil);
        
        // Sheet 2: Itemized Sales
        if (data.getItemizedSalesReport() != null) {
            createItemizedSalesSheetForSchedule(params.workbook(), data.getItemizedSalesReport(),
                    restaurant, restaurantGroup, params.startDate(), params.endDate(), headerStyle, titleStyle,
                    currencySymbol, monetaryStyle, percentageDecimalStyle, params.schedule(), userLocale, messageUtil);
        }
        
        // Sheet 3: Table-wise Sales
        if (data.getTableWiseSalesReport() != null) {
            createTableWiseSalesSheetForSchedule(params.workbook(), data.getTableWiseSalesReport(),
                    restaurant, restaurantGroup, params.startDate(), params.endDate(), headerStyle, titleStyle,
                    currencySymbol, monetaryStyle, params.schedule(), userLocale, messageUtil);
        }
        
        // Sheet 4: Discounts & Promotions
        if (data.getDiscountsPromotionsReport() != null) {
            createDiscountsPromotionsSheetForSchedule(params.workbook(), data.getDiscountsPromotionsReport(),
                    restaurant, restaurantGroup, params.startDate(), params.endDate(), headerStyle, titleStyle,
                    currencySymbol, monetaryStyle, percentageDecimalStyle, params.schedule(), userLocale, messageUtil);
        }
        
        log.info("Successfully generated overview report Excel");
    }

    /**
     * Generates the “payment and financials” report workbook content for a scheduled email.
     * <p>
     * Fetches payment/financial data from {@link ReportsService#getPaymentAndFinancials} for the date range and writes
     * reconciliation and exception-report sheets (payment, cash drawer, cancellation, refund/chargeback, wastage).
     * </p>
     *
     * @param params report generation parameters (includes workbook, scope, date range, and schedule)
     * @throws IOException when writing to the workbook fails
     * @throws EmailReportException when report data cannot be retrieved
     */
    private void generatePaymentFinancialsReportExcel(ReportGenerationParams params) throws IOException {
        
        log.info("Generating payment and financials report Excel for restaurantId: {}, restaurantGroupId: {}, startDate: {}, endDate: {}", 
                params.restaurantId(), params.restaurantGroupId(), params.startDate(), params.endDate());
        
        // Call getPaymentAndFinancials internally to get the data (without pagination for export)
        com.gulfnet.shared_library.model.response.dto.ResponseDto<com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse> paymentFinancialsResponse =
                params.reportsService().getPaymentAndFinancials(
                        null, // period - not used when startDate/endDate are provided
                        params.startDate(),
                        params.endDate(),
                        params.restaurantId(),
                        params.restaurantGroupId(),
                        1, // cancellationPage
                        10000, // cancellationSize - get all items for export
                        null, // cancellationSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        1, // chargebackPage
                        10000, // chargebackSize - get all items for export
                        null, // chargebackSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        1, // wastagePage
                        10000, // wastageSize - get all items for export
                        null, // wastageSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        1, // shiftsPage
                        10000, // shiftsSize - get all items for export
                        null, // shiftsSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        null, // shiftsStatus - get all statuses for export
                        null, // shiftsCashDrawerId
                        null, // shiftsCashierId
                        null, // shiftsSearch
                        params.startDate(), // shiftsStartDate - use the same date range as the main report
                        params.endDate(), // shiftsEndDate - use the same date range as the main report
                        params.userId(),
                        params.userRole(),
                        params.locale());
        com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse data =
                extractReportData(paymentFinancialsResponse, "Failed to get payment and financials data");
        
        ScheduledReportVenue venue = resolveRestaurantAndGroupForSchedule(params);
        com.gulfnet.shared_library.entity.Restaurant restaurant = venue.restaurant();
        com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup = venue.restaurantGroup();
        
        // Create styles
        org.apache.poi.ss.usermodel.CellStyle headerStyle = createHeaderStyle(params.workbook());
        org.apache.poi.ss.usermodel.CellStyle titleStyle = createTitleStyle(params.workbook());
        String currencySymbol = getChainCurrencySymbol();
        org.apache.poi.ss.usermodel.CellStyle monetaryStyle = createMonetaryNumberStyle(params.workbook(), currencySymbol);
        
        MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
        Locale userLocale = Locale.forLanguageTag(params.locale() != null ? params.locale() : "en");

        // Sheet 1: Payment Reconciliation + Cash Drawer Reconciliation
        createPaymentAndCashDrawerSheetForSchedule(params.workbook(), data.getPaymentReconciliationReport(), 
                data.getCashDrawerReconciliationReport(), restaurant, restaurantGroup, params.startDate(), params.endDate(), 
                headerStyle, titleStyle, monetaryStyle, currencySymbol, params.schedule(), userLocale, messageUtil);
        
        // Sheet 2: Cancellation Report
        createCancellationSheetForSchedule(params.workbook(), data.getCancellationReport(), restaurant, restaurantGroup, 
                params.startDate(), params.endDate(), headerStyle, titleStyle, monetaryStyle, currencySymbol, params.schedule(),
                userLocale, messageUtil);
        
        // Sheet 3: Refund Report
        createChargebackSheetForSchedule(params.workbook(), data.getChargebackReport(), restaurant, restaurantGroup, 
                params.startDate(), params.endDate(), headerStyle, titleStyle, monetaryStyle, currencySymbol, params.schedule(),
                userLocale, messageUtil);
        
        // Sheet 4: Wastage Report
        createWastageSheetForSchedule(params.workbook(), data.getWastageReport(), restaurant, restaurantGroup, 
                params.startDate(), params.endDate(), headerStyle, titleStyle, monetaryStyle, currencySymbol, params.schedule(),
                userLocale, messageUtil);
        
        log.info("Payment and financials report Excel generated successfully");
    }

    /**
     * Generates the “performance” report workbook content for a scheduled email.
     * <p>
     * Fetches performance data from {@link ReportsService#getPerformance} for the date range and writes sheets such as
     * sales-by-server and customer rating distribution.
     * </p>
     *
     * @param params report generation parameters (includes workbook, scope, date range, and schedule)
     * @throws IOException when writing to the workbook fails
     * @throws EmailReportException when report data cannot be retrieved
     */
    private void generatePerformanceReportExcel(ReportGenerationParams params) throws IOException {
        
        log.info("Generating performance report Excel for restaurantId: {}, restaurantGroupId: {}, startDate: {}, endDate: {}", 
                params.restaurantId(), params.restaurantGroupId(), params.startDate(), params.endDate());
        
        // Call getPerformance internally to get the data (without pagination for export)
        com.gulfnet.shared_library.model.response.dto.ResponseDto<com.gulfnet.shared_library.model.response.dto.PerformanceResponse> performanceResponse =
                params.reportsService().getPerformance(
                        null, // period - not used when startDate/endDate are provided
                        params.startDate(),
                        params.endDate(),
                        params.restaurantId(),
                        params.restaurantGroupId(),
                        1, // serverPage
                        10000, // serverSize - get all items for export
                        null, // serverSortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        1, // page
                        10000, // size - get all items for export
                        null, // sortBy
                        org.springframework.data.domain.Sort.Direction.DESC,
                        params.userId(),
                        params.userRole(),
                        params.locale());
        com.gulfnet.shared_library.model.response.dto.PerformanceResponse data =
                extractReportData(performanceResponse, "Failed to get performance data");
        
        ScheduledReportVenue venue = resolveRestaurantAndGroupForSchedule(params);
        com.gulfnet.shared_library.entity.Restaurant restaurant = venue.restaurant();
        com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup = venue.restaurantGroup();
        
        // Create styles
        org.apache.poi.ss.usermodel.CellStyle headerStyle = createHeaderStyle(params.workbook());
        org.apache.poi.ss.usermodel.CellStyle titleStyle = createTitleStyle(params.workbook());
        String currencySymbol = getChainCurrencySymbol();
        org.apache.poi.ss.usermodel.CellStyle monetaryStyle = createMonetaryNumberStyle(params.workbook(), currencySymbol);
        org.apache.poi.ss.usermodel.CellStyle percentageDecimalStyle = createNumberStyle(params.workbook());
        
        MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
        Locale userLocale = Locale.forLanguageTag(params.locale() != null ? params.locale() : "en");

        // Sheet 1: Sales By Server Report
        createSalesByServerSheetForSchedule(params.workbook(), data.getSalesByServerReport(), restaurant, restaurantGroup, 
                params.startDate(), params.endDate(), headerStyle, titleStyle, monetaryStyle, currencySymbol, params.schedule(),
                userLocale, messageUtil);
        
        // Sheet 2: Customer Rating Distribution (percentages — fixed two decimals)
        createCustomerRatingSheetForSchedule(params.workbook(), data.getCustomerRatingDistribution(), restaurant, restaurantGroup, 
                params.startDate(), params.endDate(), headerStyle, titleStyle, percentageDecimalStyle, params.schedule(),
                userLocale, messageUtil);
        
        log.info("Performance report Excel generated successfully");
    }

    /**
     * Extract report data from a generic ResponseDto or throw an EmailReportException with a custom message.
     */
    private <T> T extractReportData(
            com.gulfnet.shared_library.model.response.dto.ResponseDto<T> response,
            String errorMessage) {
        if (response == null || response.getData() == null) {
            throw new EmailReportException(errorMessage);
        }
        return response.getData();
    }

    /**
     * Resolves a Restaurant entity by ID, or null if not found.
     */
    private com.gulfnet.shared_library.entity.Restaurant resolveRestaurant(UUID restaurantId) {
        if (restaurantId == null) {
            return null;
        }
        com.gulfnet.shared_library.repository.RestaurantRepository restaurantRepository =
                applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantRepository.class);
        return restaurantRepository.findById(restaurantId).orElse(null);
    }

    /**
     * Resolves a RestaurantGroup entity by ID (only if restaurantId is null), or null if not found.
     */
    private com.gulfnet.shared_library.entity.RestaurantGroup resolveRestaurantGroup(UUID restaurantId, UUID restaurantGroupId) {
        if (restaurantId != null || restaurantGroupId == null) {
            return null;
        }
        com.gulfnet.shared_library.repository.RestaurantGroupRepository restaurantGroupRepository =
                applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantGroupRepository.class);
        return restaurantGroupRepository.findById(restaurantGroupId).orElse(null);
    }

    private ScheduledReportVenue resolveRestaurantAndGroupForSchedule(ReportGenerationParams params) {
        return new ScheduledReportVenue(
                resolveRestaurant(params.restaurantId()),
                resolveRestaurantGroup(params.restaurantId(), params.restaurantGroupId()));
    }

    /**
     * Helper method to get role name from roleId
     */
    private String getRoleName(UUID roleId, ApplicationContext applicationContext) {
        try {
            com.gulfnet.shared_library.repository.RoleRepository roleRepository = 
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RoleRepository.class);
            return roleRepository.findById(roleId)
                    .map(role -> role.getName())
                    .orElse(ROLE_HQ_ADMIN); // Default to HQ_ADMIN if role not found
        } catch (Exception e) {
            log.warn("Failed to get role name for roleId: {}, defaulting to HQ_ADMIN", roleId, e);
            return ROLE_HQ_ADMIN;
        }
    }

    /**
     * Sends a single email with the generated report attached.
     *
     * @param emailSender    email sender implementation
     * @param recipientEmail recipient email address
     * @param scheduleName   schedule name for subject/body
     * @param reportType     report type for subject/body
     * @param filename       attachment filename
     * @param reportData     attachment bytes
     * @param contentType    attachment content type
     * @param locale         locale/language tag for localization
     * @param userRole       recipient role used for greeting/template selection
     * @throws EmailReportException when sending fails
     */
    private void sendEmailWithAttachment(
            EmailSender emailSender,
            String recipientEmail,
            String scheduleName,
            ReportType reportType,
            String filename,
            byte[] reportData,
            String contentType,
            String locale,
            String userRole) {

        try {
            String subject = getEmailSubject(scheduleName, reportType, locale);
            String recipientName = resolveRecipientName(recipientEmail, locale);
            String body = getEmailBody(scheduleName, reportType, locale, userRole, recipientName);

            // Use the new sendEmailWithAttachment method
            emailSender.sendEmailWithAttachment(recipientEmail, subject, body, filename, reportData, contentType);
            
            log.info("Email with attachment sent successfully to: {}, attachment: {}", recipientEmail, filename);
            
        } catch (Exception e) {
            log.error("Failed to send email with attachment to: {}", recipientEmail, e);
            throw new EmailReportException("Failed to send email with attachment", e);
        }
    }

    /**
     * Send daily summary report to all managers of the restaurant
     */
    private void sendEmailToAllManagers(
            EmailSender emailSender,
            UUID restaurantId,
            String scheduleName,
            ReportType reportType,
            String filename,
            byte[] reportData,
            String contentType,
            ApplicationContext applicationContext) {

        try {
            // Get repositories
            com.gulfnet.shared_library.repository.UserRepository userRepository = 
                    applicationContext.getBean(com.gulfnet.shared_library.repository.UserRepository.class);
            com.gulfnet.shared_library.repository.RoleRepository roleRepository = 
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RoleRepository.class);

            // Find MANAGER role
            java.util.Optional<com.gulfnet.shared_library.entity.Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
            if (managerRoleOpt.isEmpty()) {
                // Try case-insensitive search
                java.util.List<com.gulfnet.shared_library.entity.Role> allRoles = roleRepository.findAll();
                managerRoleOpt = allRoles.stream()
                        .filter(r -> r.getName() != null && ROLE_MANAGER.equalsIgnoreCase(r.getName()))
                        .findFirst();
            }

            if (managerRoleOpt.isEmpty()) {
                log.error("MANAGER role not found in database. Cannot send daily summary report to managers.");
                throw new EmailReportException("MANAGER role not found in database");
            }

            UUID managerRoleId = managerRoleOpt.get().getId();

            // Get all active managers for the restaurant
            java.util.List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId)
                    .stream()
                    .filter(u -> u.getStatus() == com.gulfnet.shared_library.enums.EntityStatus.ACTIVE)
                    .filter(u -> u.getEmail() != null && !u.getEmail().trim().isEmpty())
                    .collect(java.util.stream.Collectors.toList());

            if (managers.isEmpty()) {
                log.warn("No active managers found for restaurant {} with valid email addresses. Cannot send daily summary report.", restaurantId);
                return;
            }

            log.info("Sending daily summary report to {} managers for restaurant {}", managers.size(), restaurantId);

            // Send email to each manager
            int successCount = 0;
            int failureCount = 0;

            for (User manager : managers) {
                Locale managerLocale = resolveUserLocale(manager);
                String subject = getEmailSubject(scheduleName, reportType, managerLocale.getLanguage());
                String managerName = resolveManagerName(manager, managerLocale);
                String body = getEmailBody(scheduleName, reportType, managerLocale.getLanguage(), ROLE_MANAGER, managerName);
                boolean sent = trySendEmailToManager(emailSender, manager, subject, body, filename, reportData, contentType);
                if (sent) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }

            log.info("Daily summary report sending completed. Success: {}, Failed: {}, Total managers: {}", 
                    successCount, failureCount, managers.size());

            if (successCount == 0) {
                throw new EmailReportException("Failed to send daily summary report to any manager");
            }

        } catch (EmailReportException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error sending daily summary report to managers for restaurant {}: {}", restaurantId, e.getMessage(), e);
            throw new EmailReportException("Failed to send daily summary report to managers", e);
        }
    }

    /**
     * Resolves a manager display name with locale-sensitive ordering (e.g., Japanese family-name first).
     *
     * @param manager manager user (nullable)
     * @param locale  locale used to determine name ordering
     * @return display name or fallback (userCode/empty)
     */
    private String resolveManagerName(User manager, Locale locale) {
        if (manager == null) {
            return "";
        }
        String first = manager.getFirstName() != null ? manager.getFirstName() : "";
        String last = manager.getLastName() != null ? manager.getLastName() : "";
        boolean isJapanese = locale != null && "ja".equalsIgnoreCase(locale.getLanguage());
        String fullName = isJapanese ? ((last + " " + first).trim()) : ((first + " " + last).trim());
        if (!fullName.isEmpty()) {
            return fullName;
        }
        return manager.getUserCode() != null ? manager.getUserCode() : "";
    }

    /**
     * Attempts to send an email with attachment to a single manager.
     * @return true if the email was sent successfully, false otherwise.
     */
    private boolean trySendEmailToManager(EmailSender emailSender, User manager, String subject, String body,
                                           String filename, byte[] reportData, String contentType) {
        try {
            emailSender.sendEmailWithAttachment(
                    manager.getEmail(), subject, body, filename, reportData, contentType);
            log.info("Daily summary report sent successfully for manager id: {}", manager.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send daily summary report for manager id: {}. Error: {}",
                    manager.getId(), e.getMessage(), e);
            // Continue sending to other managers even if one fails
            return false;
        }
    }

    /**
     * Generate CSV report for daily summary using getTodaySales method and return filename
     */
    private String generateCsvReportAndGetFilename(
            ReportsService reportsService,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale,
            ByteArrayOutputStream outputStream) throws IOException {
        
        MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        String filename = buildExportFilename(messageUtil, msgReportsExportFilenameDailySummary, userLocale, FILE_EXT_CSV);

        // Get today's sales data using getTodaySales method
        com.gulfnet.shared_library.model.response.dto.ResponseDto<com.gulfnet.shared_library.model.response.dto.TodaySalesResponse> todaySalesResponse = 
                reportsService.getTodaySales(restaurantId, restaurantGroupId, userId, userRole, locale);
        
        com.gulfnet.shared_library.model.response.dto.TodaySalesResponse salesData = todaySalesResponse.getData();
        
        if (salesData == null) {
            throw new EmailReportException("No sales data available for daily summary report");
        }

        // Get restaurant/group info for header
        String restaurantName = resolveRestaurantNameForCsv(restaurantId, restaurantGroupId, locale);

        // Generate CSV content
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            // Write BOM for Excel compatibility
            writer.write('\ufeff');
            
            // Write header
            writer.println("Daily Sales Summary Report");
            writer.println();
            
            // Restaurant info
            writer.println("Restaurant:," + restaurantName);
            writer.println("Report Date:," + startDate.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            writer.println("Generated:," + LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMATTER));
            writer.println();
            
            // Sales Summary
            writer.println("DAILY SALES SUMMARY");
            writer.println("Payment Method,Amount");
            writer.println("Total Sales," + (salesData.getTotalSales() != null ? salesData.getTotalSales() : "0.00"));
            writer.println("Cash Sales," + (salesData.getCashSales() != null ? salesData.getCashSales() : "0.00"));
            writer.println("Card Sales," + (salesData.getCardSales() != null ? salesData.getCardSales() : "0.00"));
            writer.println("UPI Sales," + (salesData.getUpiSales() != null ? salesData.getUpiSales() : "0.00"));
            
            writer.flush();
        }

        return filename;
    }

    /**
     * Resolves a restaurant/group display name for CSV reports.
     */
    private String resolveRestaurantNameForCsv(UUID restaurantId, UUID restaurantGroupId, String locale) {
        if (restaurantId != null) {
            com.gulfnet.shared_library.repository.RestaurantTranslationRepository restaurantTranslationRepository =
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantTranslationRepository.class);
            java.util.List<com.gulfnet.shared_library.entity.RestaurantTranslation> translations =
                    restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurantId);

            String translatedName = pickTranslationName(translations, locale);
            if (translatedName != null) {
                return translatedName;
            }

            com.gulfnet.shared_library.repository.RestaurantRepository restaurantRepository =
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantRepository.class);
            return restaurantRepository.findById(restaurantId)
                    .map(com.gulfnet.shared_library.entity.Restaurant::getRestaurantCode)
                    .orElse("N/A");
        } else if (restaurantGroupId != null) {
            com.gulfnet.shared_library.repository.RestaurantGroupTranslationRepository restaurantGroupTranslationRepository =
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantGroupTranslationRepository.class);
            java.util.List<com.gulfnet.shared_library.entity.RestaurantGroupTranslation> translations =
                    restaurantGroupTranslationRepository.findAllByRestaurantGroupIdWithLanguage(restaurantGroupId);

            String translatedName = pickGroupTranslationName(translations, locale);
            if (translatedName != null) {
                return translatedName;
            }

            com.gulfnet.shared_library.repository.RestaurantGroupRepository restaurantGroupRepository =
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantGroupRepository.class);
            return restaurantGroupRepository.findById(restaurantGroupId)
                    .map(com.gulfnet.shared_library.entity.RestaurantGroup::getRestaurantGroupCode)
                    .orElse("N/A");
        }
        return "N/A";
    }

    /**
     * Get schedule name from translations based on locale
     */
    private String getScheduleNameForLocale(EmailSchedule schedule, String locale, ApplicationContext applicationContext) {
        try {
            com.gulfnet.shared_library.repository.EmailScheduleTranslationRepository translationRepository = 
                    applicationContext.getBean(com.gulfnet.shared_library.repository.EmailScheduleTranslationRepository.class);
            
            java.util.List<com.gulfnet.shared_library.entity.EmailScheduleTranslation> translations =
                    translationRepository.findAllByScheduleId(schedule.getId());
            
            if (translations != null && !translations.isEmpty()) {
                // Try exact match first
                java.util.Optional<com.gulfnet.shared_library.entity.EmailScheduleTranslation> exactMatch = 
                        translations.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                                .findFirst();
                
                if (exactMatch.isPresent()) {
                    return exactMatch.get().getName();
                }
                
                // Fallback to first available translation
                return translations.get(0).getName();
            }
        } catch (Exception e) {
            log.warn("Failed to get schedule name from translations for locale: {}, using base name", locale, e);
        }
        
        // Fallback to base schedule name
        return schedule.getScheduleName();
    }

    private String getEmailSubject(String scheduleName, ReportType reportType, String locale) {
        MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
        Locale userLocale = locale != null ? new Locale(locale) : Locale.ENGLISH;

        String reportTypeName = getLocalizedReportTypeName(reportType, userLocale, messageUtil);
        return messageUtil.getMessage("email.scheduled.report.title", userLocale, scheduleName, reportTypeName);
    }

    /**
     * Builds the localized HTML email body for the scheduled report notification.
     * <p>
     * Escapes dynamic values and includes schedule/report metadata plus a generated-at timestamp.
     * </p>
     *
     * @param scheduleName   schedule display name
     * @param reportType     report type
     * @param locale         locale/language tag string
     * @param userRole       recipient role
     * @param recipientName  name used in the greeting
     * @return HTML email body
     */
    private String getEmailBody(String scheduleName, ReportType reportType, String locale, String userRole, String recipientName) {
        MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
        Locale userLocale = locale != null ? new Locale(locale) : Locale.ENGLISH;

        String reportTypeName = getLocalizedReportTypeName(reportType, userLocale, messageUtil);

        // Determine greeting based on user role (localized)
        String greeting = ROLE_HQ_ADMIN.equalsIgnoreCase(userRole)
                ? messageUtil.getMessage("email.scheduled.report.greeting.hq_admin", userLocale, recipientName)
                : messageUtil.getMessage("email.scheduled.report.greeting.user", userLocale, recipientName);
        
        // Convert UTC time to local time (using Asia/Kolkata as default, adjust as needed)
        ZoneId localZoneId = ZoneId.of("Asia/Kolkata"); // Default timezone
        LocalDateTime utcTime = LocalDateTime.now(ZoneOffset.UTC);
        java.time.ZonedDateTime localTime = utcTime.atZone(ZoneOffset.UTC).withZoneSameInstant(localZoneId);
        
        // Format in 12-hour format with AM/PM
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");
        String formattedTime = localTime.format(formatter);
        
        String safeScheduleName = escapeHtml(scheduleName);
        String safeGreeting = escapeHtml(greeting);
        String safeReportTypeName = escapeHtml(reportTypeName);
        String safeFormattedTime = escapeHtml(formattedTime);

        String titleText = messageUtil.getMessage("email.scheduled.report.title", userLocale);
        String reportLabel = messageUtil.getMessage("email.scheduled.report.label.report", userLocale);
        String scheduleLabel = messageUtil.getMessage("email.scheduled.report.label.schedule", userLocale);
        String attachmentLineHtml = messageUtil.getMessage(
                "email.scheduled.report.attachment.line",
                userLocale,
                safeReportTypeName);
        String generatedAtLabel = messageUtil.getMessage("email.scheduled.report.generated_at.label", userLocale);

        String bestRegards = messageUtil.getMessage("email.receipt.regards", userLocale);
        String companyName = messageUtil.getMessage("email.common.restaurant.management.system.name", userLocale);

        // Table-based HTML for consistent alignment across email clients.
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
                + "<tr><td style=\"background:#2563eb;height:10px;\">&nbsp;</td></tr>"
                + "<tr>"
                + "<td style=\"padding:20px 24px 8px 24px;\">"
                + "<div style=\"font-size:18px;color:#111827;font-weight:700;line-height:24px;\">"
                + escapeHtml(titleText)
                + ScheduledReportEmailHtml.DIV_CLOSE
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:0 24px 12px 24px;\">"
                + "<div style=\"font-size:14px;color:#4b5563;line-height:20px;\">"
                + safeGreeting
                + ScheduledReportEmailHtml.DIV_CLOSE
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding: 0 24px 16px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid #e5e7eb;border-radius:12px;background:#f9fafb;\">"
                + "<tr>"
                + "<td style=\"padding:14px 16px 8px 16px;\">"
                + "<div style=\"font-size:14px;color:#111827;font-weight:700;\">"
                + escapeHtml(reportLabel) + " "
                + safeReportTypeName
                + ScheduledReportEmailHtml.DIV_CLOSE
                + "<div style=\"font-size:13px;color:#6b7280;margin-top:4px;\">"
                + escapeHtml(scheduleLabel) + " "
                + safeScheduleName
                + ScheduledReportEmailHtml.DIV_CLOSE
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding: 0 16px 14px 16px;\">"
                + "<div style=\"font-size:14px;color:#374151;line-height:22px;\">"
                + attachmentLineHtml
                + ScheduledReportEmailHtml.DIV_CLOSE
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:0 16px 16px 16px;\">"
                + "<div style=\"font-size:13px;color:#6b7280;\">"
                + escapeHtml(generatedAtLabel) + " <strong style=\"color:#111827;\">"
                + safeFormattedTime
                + "</strong></div>"
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + ScheduledReportEmailHtml.TABLE_CLOSE
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:0 24px 22px 24px;\">"
                + "<div style=\"font-size:13px;color:#6b7280;line-height:20px;\">"
                + escapeHtml(bestRegards)
                + "<br/>"
                + escapeHtml(companyName)
                + ScheduledReportEmailHtml.DIV_CLOSE
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + ScheduledReportEmailHtml.TABLE_CLOSE
                + ScheduledReportEmailHtml.TD_CLOSE
                + ScheduledReportEmailHtml.TR_CLOSE
                + ScheduledReportEmailHtml.TABLE_CLOSE
                + "</body>"
                + "</html>";
    }

    /**
     * Resolves a recipient display name from their email address (best-effort).
     *
     * @param recipientEmail recipient email
     * @param locale         locale/language tag (used for name ordering)
     * @return resolved name or empty string when not found/failed
     */
    private String resolveRecipientName(String recipientEmail, String locale) {
        try {
            com.gulfnet.shared_library.repository.UserRepository userRepository =
                    applicationContext.getBean(com.gulfnet.shared_library.repository.UserRepository.class);
            User recipient = userRepository.findByEmail(recipientEmail).orElse(null);
            if (recipient == null) {
                return "";
            }
            Locale userLocale = locale != null ? new Locale(locale) : Locale.ENGLISH;
            String first = recipient.getFirstName() != null ? recipient.getFirstName() : "";
            String last = recipient.getLastName() != null ? recipient.getLastName() : "";
            boolean isJapanese = "ja".equalsIgnoreCase(userLocale.getLanguage());
            String fullName = isJapanese ? ((last + " " + first).trim()) : ((first + " " + last).trim());
            if (!fullName.isEmpty()) {
                return fullName;
            }
            return recipient.getUserCode() != null ? recipient.getUserCode() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String getLocalizedReportTypeName(ReportType reportType, Locale userLocale, MessageUtil messageUtil) {
        // Each ReportType code must exist in messages_*.properties under this prefix.
        String key = "email.scheduled.report.type." + reportType.name();
        try {
            return messageUtil.getMessage(key, userLocale);
        } catch (NoSuchMessageException e) {
            // Never fail the job due to missing translation keys.
            return reportType != null && reportType.getDisplayName() != null ? reportType.getDisplayName() : "Report";
        }
    }

    /**
     * Resolves a supported locale for the given user, defaulting to English.
     *
     * @param user user whose language code may be used
     * @return resolved locale within supported languages, or {@link Locale#ENGLISH}
     */
    private Locale resolveUserLocale(User user) {
        java.util.Set<String> supportedLanguages = localizationProperties != null
                && localizationProperties.getLanguages() != null
                ? localizationProperties.getLanguages().stream()
                .filter(lang -> lang != null && !lang.trim().isEmpty())
                .map(lang -> lang.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet())
                : java.util.Set.of("en");

        if (user != null && user.getLanguageCode() != null && !user.getLanguageCode().trim().isEmpty()) {
            String normalizedLang = Locale.forLanguageTag(user.getLanguageCode().trim()).getLanguage();
            if (supportedLanguages.contains(normalizedLang)) {
                return Locale.forLanguageTag(normalizedLang);
            }
        }
        return Locale.ENGLISH;
    }

    private String escapeHtml(String input) {
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
    
    private String getReportTypeDisplayName(ReportType reportType) {
        // Fallback display name (should be overridden by localized message keys for emails).
        return reportType.getDisplayName();
    }

    /**
     * Builds a report title string from the schedule's report type.
     */
    private String buildReportTitle(EmailSchedule schedule) {
        return getReportTypeDisplayName(schedule.getReportType()).toUpperCase() + REPORT_TITLE_SUFFIX;
    }

    // Excel generation helper methods
    /**
     * Creates a header style used across generated Excel sheets.
     *
     * @param workbook workbook to create the style in
     * @return header style (bold, grey fill, bordered, centered)
     */
    private org.apache.poi.ss.usermodel.CellStyle createHeaderStyle(Workbook workbook) {
        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        return style;
    }

    private org.apache.poi.ss.usermodel.CellStyle createTitleStyle(Workbook workbook) {
        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private org.apache.poi.ss.usermodel.CellStyle createNumberStyle(Workbook workbook) {
        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private String getChainCurrencySymbol() {
        if (restaurantChainConfigProperties != null && restaurantChainConfigProperties.getChain() != null) {
            return restaurantChainConfigProperties.getChain().getCurrency();
        }
        return null;
    }

    private org.apache.poi.ss.usermodel.CellStyle createMonetaryNumberStyle(Workbook workbook, String currencySymbol) {
        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(CurrencyFormatter.getMonetaryExcelDataFormatPattern(currencySymbol)));
        return style;
    }

    /**
     * Creates the “Summary & Payment Types” sheet for scheduled overview exports.
     */
    private void createSummarySheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.DailySalesSummary dailySalesSummary,
            java.util.List<com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.PaymentTypeBreakdown> paymentTypesBreakdown,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            String currencySymbol,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            org.apache.poi.ss.usermodel.CellStyle percentageDecimalStyle,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.summaryPaymentTypes", userLocale));
        int rowNum = 0;

        // Title - use report type from schedule
        String reportTitle = buildReportTitle(schedule);
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(reportTitle);
        titleCell.setCellStyle(titleStyle);

        // Restaurant info
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        // Daily Sales Summary Section
        if (dailySalesSummary != null) {
            rowNum++;
            org.apache.poi.ss.usermodel.Row summaryHeaderRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell summaryHeaderCell = summaryHeaderRow.createCell(0);
            summaryHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.dailySalesSummary", userLocale));
            summaryHeaderCell.setCellStyle(titleStyle);

            org.apache.poi.ss.usermodel.Row summaryLabelsRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Row summaryValuesRow = sheet.createRow(rowNum++);

            String[] summaryLabels = {
                    messageUtil.getMessage("csv.dashboard.metric.total.sales", userLocale),
                    messageUtil.getMessage("csv.dashboard.metric.total.orders", userLocale),
                    messageUtil.getMessage("reports.export.header.totalTablesServed", userLocale),
                    messageUtil.getMessage("reports.export.header.averageOrderValue", userLocale)
            };
            Object[] summaryValues = {
                dailySalesSummary.getTotalSales(),
                dailySalesSummary.getTotalOrders(),
                dailySalesSummary.getTotalTablesServed(),
                dailySalesSummary.getAvgOrderValue()
            };

            for (int i = 0; i < summaryLabels.length; i++) {
                org.apache.poi.ss.usermodel.Cell labelCell = summaryLabelsRow.createCell(i);
                labelCell.setCellValue(summaryLabels[i]);
                labelCell.setCellStyle(headerStyle);

                org.apache.poi.ss.usermodel.Cell valueCell = summaryValuesRow.createCell(i);
                if (summaryValues[i] instanceof java.math.BigDecimal) {
                    valueCell.setCellValue(CurrencyFormatter.formatAmount((java.math.BigDecimal) summaryValues[i], currencySymbol).doubleValue());
                    valueCell.setCellStyle(monetaryStyle);
                } else if (summaryValues[i] instanceof Long) {
                    valueCell.setCellValue(((Long) summaryValues[i]).doubleValue());
                } else {
                    setCellValueFromObject(valueCell, summaryValues[i], monetaryStyle);
                }
            }
        }

        // Payment Types Breakdown Section
        if (paymentTypesBreakdown != null && !paymentTypesBreakdown.isEmpty()) {
            rowNum += 2;
            org.apache.poi.ss.usermodel.Row paymentHeaderRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell paymentHeaderCell = paymentHeaderRow.createCell(0);
            paymentHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.paymentTypesBreakdown", userLocale));
            paymentHeaderCell.setCellStyle(titleStyle);

            org.apache.poi.ss.usermodel.Row paymentLabelsRow = sheet.createRow(rowNum++);
            String[] paymentHeaders = {
                    messageUtil.getMessage("reports.export.header.paymentMethod", userLocale),
                    messageUtil.getMessage("reports.export.header.percentage", userLocale),
                    messageUtil.getMessage("csv.dashboard.metric.total.sales", userLocale)
            };
            for (int i = 0; i < paymentHeaders.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = paymentLabelsRow.createCell(i);
                cell.setCellValue(paymentHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            for (com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.PaymentTypeBreakdown payment : paymentTypesBreakdown) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "N/A");
                org.apache.poi.ss.usermodel.Cell pctCell = row.createCell(1);
                if (payment.getPercentage() != null) {
                    pctCell.setCellValue(payment.getPercentage());
                    pctCell.setCellStyle(percentageDecimalStyle);
                }
                org.apache.poi.ss.usermodel.Cell amountCell = row.createCell(2);
                if (payment.getTotalSales() != null) {
                    amountCell.setCellValue(CurrencyFormatter.formatAmount(payment.getTotalSales(), currencySymbol).doubleValue());
                    amountCell.setCellStyle(monetaryStyle);
                }
            }
        }

        // Auto-size columns
        for (int i = 0; i < 10; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Sets a cell value from an Object, applying numberStyle for numeric types.
     */
    private void setCellValueFromObject(org.apache.poi.ss.usermodel.Cell cell, Object value,
                                         org.apache.poi.ss.usermodel.CellStyle numberStyle) {
        if (value == null) {
            return;
        }
        if (value instanceof java.math.BigDecimal) {
            cell.setCellValue(((java.math.BigDecimal) value).doubleValue());
            cell.setCellStyle(numberStyle);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(numberStyle);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Creates the “Itemized Sales” sheet for scheduled overview exports.
     */
    private void createItemizedSalesSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.ItemizedSalesReport itemizedReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            String currencySymbol,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            org.apache.poi.ss.usermodel.CellStyle percentageDecimalStyle,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.itemizedSales", userLocale));
        int rowNum = 0;
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("csv.dashboard.header.item.name", userLocale),
                messageUtil.getMessage("csv.dashboard.header.category", userLocale),
                messageUtil.getMessage("reports.export.header.quantitySold", userLocale),
                messageUtil.getMessage("reports.export.header.unitPrice", userLocale),
                messageUtil.getMessage("csv.dashboard.metric.total.sales", userLocale),
                messageUtil.getMessage("reports.export.header.percentageOfTotalSales", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (itemizedReport != null && itemizedReport.getItems() != null) {
            for (com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.ItemizedSalesItem item : itemizedReport.getItems()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getItemName() != null ? item.getItemName() : "N/A");
                row.createCell(1).setCellValue(item.getCategory() != null ? item.getCategory() : "N/A");
                row.createCell(2).setCellValue(item.getQuantitySold() != null ? item.getQuantitySold() : 0);

                org.apache.poi.ss.usermodel.Cell unitPriceCell = row.createCell(3);
                if (item.getUnitPrice() != null) {
                    unitPriceCell.setCellValue(CurrencyFormatter.formatAmount(item.getUnitPrice(), currencySymbol).doubleValue());
                    unitPriceCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell totalSalesCell = row.createCell(4);
                if (item.getTotalSales() != null) {
                    totalSalesCell.setCellValue(CurrencyFormatter.formatAmount(item.getTotalSales(), currencySymbol).doubleValue());
                    totalSalesCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell percentageCell = row.createCell(5);
                if (item.getPercentageOfTotalSales() != null) {
                    percentageCell.setCellValue(item.getPercentageOfTotalSales());
                    percentageCell.setCellStyle(percentageDecimalStyle);
                }
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates the “Table-wise Sales” sheet for scheduled overview exports.
     */
    private void createTableWiseSalesSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.TableWiseSalesReport tableWiseReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            String currencySymbol,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.tableWiseSales", userLocale));
        int rowNum = 0;
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.tableNo", userLocale),
                messageUtil.getMessage("csv.dashboard.metric.total.orders", userLocale),
                messageUtil.getMessage("csv.dashboard.metric.total.sales", userLocale),
                messageUtil.getMessage("reports.export.header.averageOrderValue", userLocale),
                messageUtil.getMessage("reports.export.header.totalTax", userLocale),
                messageUtil.getMessage("reports.export.header.totalServiceCharge", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (tableWiseReport != null && tableWiseReport.getTables() != null) {
            for (com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.TableWiseSalesItem table : tableWiseReport.getTables()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(table.getTableNo() != null ? table.getTableNo() : "N/A");
                row.createCell(1).setCellValue(table.getTotalOrders() != null ? table.getTotalOrders() : 0);

                org.apache.poi.ss.usermodel.Cell totalSalesCell = row.createCell(2);
                if (table.getTotalSales() != null) {
                    totalSalesCell.setCellValue(CurrencyFormatter.formatAmount(table.getTotalSales(), currencySymbol).doubleValue());
                    totalSalesCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell avgOrderCell = row.createCell(3);
                if (table.getAverageOrderValue() != null) {
                    avgOrderCell.setCellValue(CurrencyFormatter.formatAmount(table.getAverageOrderValue(), currencySymbol).doubleValue());
                    avgOrderCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell taxCell = row.createCell(4);
                if (table.getTotalTax() != null) {
                    taxCell.setCellValue(CurrencyFormatter.formatAmount(table.getTotalTax(), currencySymbol).doubleValue());
                    taxCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell serviceChargeCell = row.createCell(5);
                if (table.getTotalServiceCharge() != null) {
                    serviceChargeCell.setCellValue(CurrencyFormatter.formatAmount(table.getTotalServiceCharge(), currencySymbol).doubleValue());
                    serviceChargeCell.setCellStyle(monetaryStyle);
                }
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates the “Discounts & Promotions” sheet for scheduled overview exports.
     */
    private void createDiscountsPromotionsSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.DiscountsPromotionsReport discountsReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            String currencySymbol,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            org.apache.poi.ss.usermodel.CellStyle percentageDecimalStyle,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.discountsPromotions", userLocale));
        int rowNum = 0;
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.discountName", userLocale),
                messageUtil.getMessage("reports.export.header.numberOfTransactions", userLocale),
                messageUtil.getMessage("reports.export.header.totalDiscountApplied", userLocale),
                messageUtil.getMessage("reports.export.header.totalRevenue", userLocale),
                messageUtil.getMessage("reports.export.header.totalRevenueBeforeDiscount", userLocale),
                messageUtil.getMessage("reports.export.header.discountEfficiency", userLocale),
                messageUtil.getMessage("reports.export.header.appliedTo", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (discountsReport != null && discountsReport.getDiscounts() != null) {
            for (com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse.DiscountPromotionItem discount : discountsReport.getDiscounts()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(discount.getDiscountType() != null ? discount.getDiscountType() : "N/A");
                row.createCell(1).setCellValue(discount.getNumberOfTransactions() != null ? discount.getNumberOfTransactions() : 0);

                org.apache.poi.ss.usermodel.Cell discountCell = row.createCell(2);
                if (discount.getTotalDiscountApplied() != null) {
                    discountCell.setCellValue(CurrencyFormatter.formatAmount(discount.getTotalDiscountApplied(), currencySymbol).doubleValue());
                    discountCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell revenueCell = row.createCell(3);
                if (discount.getTotalRevenue() != null) {
                    revenueCell.setCellValue(CurrencyFormatter.formatAmount(discount.getTotalRevenue(), currencySymbol).doubleValue());
                    revenueCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell revenueBeforeCell = row.createCell(4);
                if (discount.getTotalRevenueBeforeDiscount() != null) {
                    revenueBeforeCell.setCellValue(CurrencyFormatter.formatAmount(discount.getTotalRevenueBeforeDiscount(), currencySymbol).doubleValue());
                    revenueBeforeCell.setCellStyle(monetaryStyle);
                }

                org.apache.poi.ss.usermodel.Cell efficiencyCell = row.createCell(5);
                setCellValueFromObject(efficiencyCell, discount.getDiscountEfficiency(), percentageDecimalStyle);

                row.createCell(6).setCellValue(discount.getAppliedTo() != null ? discount.getAppliedTo() : "N/A");
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Resolves a display name from a restaurant group entity.
     */
    private String resolveGroupDisplayName(com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup, Locale userLocale) {
        if (restaurantGroup != null && restaurantGroup.getId() != null) {
            com.gulfnet.shared_library.repository.RestaurantGroupTranslationRepository restaurantGroupTranslationRepository =
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantGroupTranslationRepository.class);
            java.util.List<com.gulfnet.shared_library.entity.RestaurantGroupTranslation> translations =
                    restaurantGroupTranslationRepository.findAllByRestaurantGroupIdWithLanguage(restaurantGroup.getId());

            String translatedName = pickGroupTranslationName(translations, userLocale != null ? userLocale.getLanguage() : null);
            if (translatedName != null) {
                return translatedName;
            }
        }
        if (restaurantGroup.getRestaurantGroupCode() != null) {
            return restaurantGroup.getRestaurantGroupCode();
        }
        return "N/A";
    }

    /**
     * Resolves a display name from a restaurant entity.
     */
    private String resolveRestaurantDisplayName(com.gulfnet.shared_library.entity.Restaurant restaurant, Locale userLocale) {
        if (restaurant != null && restaurant.getId() != null) {
            com.gulfnet.shared_library.repository.RestaurantTranslationRepository restaurantTranslationRepository =
                    applicationContext.getBean(com.gulfnet.shared_library.repository.RestaurantTranslationRepository.class);
            java.util.List<com.gulfnet.shared_library.entity.RestaurantTranslation> translations =
                    restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurant.getId());

            String translatedName = pickTranslationName(translations, userLocale != null ? userLocale.getLanguage() : null);
            if (translatedName != null) {
                return translatedName;
            }
        }
        if (restaurant.getRestaurantCode() != null) {
            return restaurant.getRestaurantCode();
        }
        return "N/A";
    }

    /**
     * Writes standard report metadata (scope, date range, export date) at the top of a sheet.
     *
     * @return next available row index after the metadata rows
     */
    private int addReportInfoForSchedule(org.apache.poi.ss.usermodel.Sheet sheet, int startRow,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        int rowNum = startRow;

        org.apache.poi.ss.usermodel.Row restaurantRow = sheet.createRow(rowNum++);
        if (restaurantGroup != null) {
            restaurantRow.createCell(0).setCellValue(messageUtil.getMessage("reports.export.label.restaurantGroup", userLocale));
            restaurantRow.createCell(1).setCellValue(resolveGroupDisplayName(restaurantGroup, userLocale));
        } else if (restaurant != null) {
            restaurantRow.createCell(0).setCellValue(messageUtil.getMessage("reports.export.label.restaurant", userLocale));
            restaurantRow.createCell(1).setCellValue(resolveRestaurantDisplayName(restaurant, userLocale));
        }

        org.apache.poi.ss.usermodel.Row dateRangeRow = sheet.createRow(rowNum++);
        dateRangeRow.createCell(0).setCellValue(messageUtil.getMessage("reports.export.label.dateRange", userLocale));
        String toSeparator = messageUtil.getMessage("reports.export.value.toSeparator", userLocale);
        String dateRange = startDateTime.format(DATETIME_FORMATTER) + toSeparator + endDateTime.format(DATETIME_FORMATTER);
        dateRangeRow.createCell(1).setCellValue(dateRange);

        org.apache.poi.ss.usermodel.Row exportDateRow = sheet.createRow(rowNum++);
        exportDateRow.createCell(0).setCellValue(messageUtil.getMessage("csv.export.date", userLocale));
        exportDateRow.createCell(1).setCellValue(LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMATTER));

        return rowNum;
    }

    private String pickTranslationName(
            java.util.List<com.gulfnet.shared_library.entity.RestaurantTranslation> translations,
            String preferredLanguageCode) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }
        if (preferredLanguageCode != null) {
            for (com.gulfnet.shared_library.entity.RestaurantTranslation translation : translations) {
                if (translation != null
                        && translation.getLanguageCode() != null
                        && preferredLanguageCode.equalsIgnoreCase(translation.getLanguageCode())
                        && translation.getName() != null
                        && !translation.getName().isBlank()) {
                    return translation.getName();
                }
            }
        }
        for (com.gulfnet.shared_library.entity.RestaurantTranslation translation : translations) {
            if (translation != null && translation.getName() != null && !translation.getName().isBlank()) {
                return translation.getName();
            }
        }
        return null;
    }

    private String pickGroupTranslationName(
            java.util.List<com.gulfnet.shared_library.entity.RestaurantGroupTranslation> translations,
            String preferredLanguageCode) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }
        if (preferredLanguageCode != null) {
            for (com.gulfnet.shared_library.entity.RestaurantGroupTranslation translation : translations) {
                if (translation != null
                        && translation.getLanguageCode() != null
                        && preferredLanguageCode.equalsIgnoreCase(translation.getLanguageCode())
                        && translation.getName() != null
                        && !translation.getName().isBlank()) {
                    return translation.getName();
                }
            }
        }
        for (com.gulfnet.shared_library.entity.RestaurantGroupTranslation translation : translations) {
            if (translation != null && translation.getName() != null && !translation.getName().isBlank()) {
                return translation.getName();
            }
        }
        return null;
    }

    // Payment and Financials sheet creation methods
    /**
     * Creates the “Payment & Cash Drawer” sheet combining payment and cash drawer reconciliation sections.
     */
    private void createPaymentAndCashDrawerSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.PaymentReconciliationReport paymentReport,
            com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.CashDrawerReconciliationReport cashDrawerReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            String currencySymbol,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.paymentCashDrawer", userLocale));
        int rowNum = 0;
        
        // Main Report Title
        String reportTitle = buildReportTitle(schedule);
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(reportTitle);
        titleCell.setCellStyle(titleStyle);
        
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        // Payment Reconciliation Section
        rowNum = writePaymentReconciliationSection(sheet, paymentReport, rowNum, headerStyle, titleStyle, monetaryStyle, currencySymbol, userLocale, messageUtil);

        // Cash Drawer Reconciliation Section
        writeCashDrawerReconciliationSection(sheet, cashDrawerReport, rowNum, headerStyle, titleStyle, monetaryStyle, currencySymbol, userLocale, messageUtil);

        // Auto-size columns
        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Writes the payment reconciliation section into the given sheet.
     *
     * @return next row index after the section
     */
    private int writePaymentReconciliationSection(org.apache.poi.ss.usermodel.Sheet sheet,
            com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.PaymentReconciliationReport paymentReport,
            int rowNum,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            String currencySymbol,
            Locale userLocale,
            MessageUtil messageUtil) {
        rowNum++;
        org.apache.poi.ss.usermodel.Row paymentHeaderRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell paymentHeaderCell = paymentHeaderRow.createCell(0);
        paymentHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.paymentReconciliationReport", userLocale));
        paymentHeaderCell.setCellStyle(titleStyle);

        org.apache.poi.ss.usermodel.Row paymentDataHeaderRow = sheet.createRow(rowNum++);
        String[] paymentHeaders = {
                messageUtil.getMessage("reports.export.header.paymentMethod", userLocale),
                messageUtil.getMessage("reports.export.header.totalTransactions", userLocale),
                messageUtil.getMessage("reports.export.header.totalAmount", userLocale)
        };
        for (int i = 0; i < paymentHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = paymentDataHeaderRow.createCell(i);
            cell.setCellValue(paymentHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        if (paymentReport != null && paymentReport.getPayments() != null) {
            for (com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.PaymentReconciliationItem payment : paymentReport.getPayments()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "N/A");
                row.createCell(1).setCellValue(payment.getTotalTransactions() != null ? payment.getTotalTransactions() : 0);
                org.apache.poi.ss.usermodel.Cell amountCell = row.createCell(2);
                if (payment.getTotalAmount() != null) {
                    amountCell.setCellValue(CurrencyFormatter.formatAmount(payment.getTotalAmount(), currencySymbol).doubleValue());
                    amountCell.setCellStyle(monetaryStyle);
                }
            }
        }
        return rowNum;
    }

    /**
     * Writes the cash drawer reconciliation section into the given sheet.
     */
    private void writeCashDrawerReconciliationSection(org.apache.poi.ss.usermodel.Sheet sheet,
            com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.CashDrawerReconciliationReport cashDrawerReport,
            int rowNum,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            String currencySymbol,
            Locale userLocale,
            MessageUtil messageUtil) {
        rowNum += 2;
        org.apache.poi.ss.usermodel.Row cashDrawerHeaderRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell cashDrawerHeaderCell = cashDrawerHeaderRow.createCell(0);
        cashDrawerHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.cashDrawerReconciliationReport", userLocale));
        cashDrawerHeaderCell.setCellStyle(titleStyle);

        org.apache.poi.ss.usermodel.Row cashDrawerDataHeaderRow = sheet.createRow(rowNum++);
        String[] cashDrawerHeaders = {
                messageUtil.getMessage("reports.export.header.metric", userLocale),
                messageUtil.getMessage("reports.export.header.value", userLocale)
        };
        for (int i = 0; i < cashDrawerHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = cashDrawerDataHeaderRow.createCell(i);
            cell.setCellValue(cashDrawerHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        if (cashDrawerReport != null) {
            String[] metrics = {
                    messageUtil.getMessage("reports.export.cashDrawer.metric.openingBalance", userLocale),
                    messageUtil.getMessage("reports.export.cashDrawer.metric.totalCashSalesNet", userLocale),
                    messageUtil.getMessage("reports.export.cashDrawer.metric.totalCashRefundsPaidNet", userLocale),
                    messageUtil.getMessage("reports.export.cashDrawer.metric.cashWithdrawal", userLocale),
                    messageUtil.getMessage("reports.export.cashDrawer.metric.expectedCashBalance", userLocale),
                    messageUtil.getMessage("reports.export.cashDrawer.metric.actualCashBalance", userLocale),
                    messageUtil.getMessage("reports.export.cashDrawer.metric.discrepancyAmount", userLocale),
                    messageUtil.getMessage("reports.export.cashDrawer.metric.status", userLocale)
            };
            java.math.BigDecimal[] values = {
                    cashDrawerReport.getOpeningBalance(),
                    cashDrawerReport.getTotalCashSales(),
                    cashDrawerReport.getTotalCashRefundsPaid(),
                    cashDrawerReport.getCashWithdrawal(),
                    cashDrawerReport.getExpectedCashBalance(),
                    cashDrawerReport.getActualCashBalance(),
                    cashDrawerReport.getDiscrepancyAmount(),
                    null // Status is a string
            };
            String statusValue = cashDrawerReport.getStatus() != null ? cashDrawerReport.getStatus() : "balanced";

            for (int i = 0; i < metrics.length; i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(metrics[i]);
                org.apache.poi.ss.usermodel.Cell valueCell = row.createCell(1);
                if (i == metrics.length - 1) {
                    // Status is a string
                    valueCell.setCellValue(statusValue);
                } else if (values[i] != null) {
                    valueCell.setCellValue(CurrencyFormatter.formatAmount(values[i], currencySymbol).doubleValue());
                    valueCell.setCellStyle(monetaryStyle);
                } else {
                    valueCell.setCellValue(CurrencyFormatter.formatAmount(BigDecimal.ZERO, currencySymbol).doubleValue());
                    valueCell.setCellStyle(monetaryStyle);
                }
            }
        }
    }

    /**
     * Creates the “Cancellation Report” sheet for scheduled payment/financial exports.
     */
    private void createCancellationSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.CancellationReport cancellationReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            String currencySymbol,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.cancellationReport", userLocale));
        int rowNum = 0;
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.id", userLocale),
                messageUtil.getMessage("reports.export.header.type", userLocale),
                messageUtil.getMessage("reports.export.header.dateTime", userLocale),
                messageUtil.getMessage("reports.export.header.amount", userLocale),
                messageUtil.getMessage("reports.export.header.paymentMethod", userLocale),
                messageUtil.getMessage("reports.export.header.reason", userLocale),
                messageUtil.getMessage("reports.export.header.initiatedBy", userLocale),
                messageUtil.getMessage("reports.export.header.cancelledBy", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (cancellationReport != null && cancellationReport.getCancellations() != null) {
            for (com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.CancellationItem cancellation : cancellationReport.getCancellations()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(cancellation.getId() != null ? cancellation.getId() : "N/A");
                row.createCell(1).setCellValue(cancellation.getType() != null ? cancellation.getType() : "N/A");
                row.createCell(2).setCellValue(cancellation.getDateTime() != null ?
                        cancellation.getDateTime().format(DATETIME_FORMATTER) : "N/A");
                org.apache.poi.ss.usermodel.Cell amountCell = row.createCell(3);
                if (cancellation.getAmount() != null) {
                    amountCell.setCellValue(CurrencyFormatter.formatAmount(cancellation.getAmount(), currencySymbol).doubleValue());
                    amountCell.setCellStyle(monetaryStyle);
                }
                row.createCell(4).setCellValue(cancellation.getPaymentMethod() != null ? cancellation.getPaymentMethod() : "N/A");
                row.createCell(5).setCellValue(cancellation.getReason() != null ? cancellation.getReason() : "N/A");
                row.createCell(6).setCellValue(cancellation.getInitiatedBy() != null ? cancellation.getInitiatedBy() : "N/A");
                row.createCell(7).setCellValue(cancellation.getCancelledBy() != null ? cancellation.getCancelledBy() : "N/A");
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates the “Refund Report” (chargeback/refund items) sheet for scheduled payment/financial exports.
     */
    private void createChargebackSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.ChargebackReport chargebackReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            String currencySymbol,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.refundReport", userLocale));
        int rowNum = 0;
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.transactionId", userLocale),
                messageUtil.getMessage("reports.export.header.dateTime", userLocale),
                messageUtil.getMessage("reports.export.header.amount", userLocale),
                messageUtil.getMessage("reports.export.header.paymentMethod", userLocale),
                messageUtil.getMessage("reports.export.header.reason", userLocale),
                messageUtil.getMessage("reports.export.header.bankStatus", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (chargebackReport != null && chargebackReport.getChargebacks() != null) {
            for (com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.ChargebackItem chargeback : chargebackReport.getChargebacks()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(chargeback.getTransactionId() != null ? chargeback.getTransactionId() : "N/A");
                row.createCell(1).setCellValue(chargeback.getDateTime() != null ?
                        chargeback.getDateTime().format(DATETIME_FORMATTER) : "N/A");
                org.apache.poi.ss.usermodel.Cell amountCell = row.createCell(2);
                if (chargeback.getAmount() != null) {
                    amountCell.setCellValue(CurrencyFormatter.formatAmount(chargeback.getAmount(), currencySymbol).doubleValue());
                    amountCell.setCellStyle(monetaryStyle);
                }
                row.createCell(3).setCellValue(chargeback.getPaymentMethod() != null ? chargeback.getPaymentMethod() : "N/A");
                row.createCell(4).setCellValue(chargeback.getReason() != null ? chargeback.getReason() : "N/A");
                row.createCell(5).setCellValue(chargeback.getBankStatus() != null ? chargeback.getBankStatus() : "Pending");
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates the “Wastage Report” sheet for scheduled payment/financial exports.
     */
    private void createWastageSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.WastageReport wastageReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            String currencySymbol,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.wastageReport", userLocale));
        int rowNum = 0;
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("csv.dashboard.header.item.name", userLocale),
                messageUtil.getMessage("csv.dashboard.header.category", userLocale),
                messageUtil.getMessage("reports.export.header.quantityWasted", userLocale),
                messageUtil.getMessage("reports.export.header.totalWastageCost", userLocale),
                messageUtil.getMessage("reports.export.header.dateTime", userLocale),
                messageUtil.getMessage("reports.export.header.reason", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (wastageReport != null && wastageReport.getWastageItems() != null) {
            for (com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse.WastageItem wastage : wastageReport.getWastageItems()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(wastage.getItemName() != null ? wastage.getItemName() : "N/A");
                row.createCell(1).setCellValue(wastage.getCategory() != null ? wastage.getCategory() : "N/A");
                row.createCell(2).setCellValue(wastage.getQuantityWasted() != null ? wastage.getQuantityWasted() : 0);
                org.apache.poi.ss.usermodel.Cell costCell = row.createCell(3);
                if (wastage.getTotalWastageCost() != null) {
                    costCell.setCellValue(CurrencyFormatter.formatAmount(wastage.getTotalWastageCost(), currencySymbol).doubleValue());
                    costCell.setCellStyle(monetaryStyle);
                }
                row.createCell(4).setCellValue(wastage.getDateTime() != null ?
                        wastage.getDateTime().format(DATETIME_FORMATTER) : "N/A");
                row.createCell(5).setCellValue(wastage.getReason() != null ? wastage.getReason() : "N/A");
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Performance report sheet creation methods
    /**
     * Creates the “Sales By Server” sheet for scheduled performance exports.
     */
    private void createSalesByServerSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.PerformanceResponse.SalesByServerReport salesByServerReport,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle monetaryStyle,
            String currencySymbol,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.salesByServer", userLocale));
        int rowNum = 0;
        
        // Main Report Title
        String reportTitle = buildReportTitle(schedule);
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(reportTitle);
        titleCell.setCellStyle(titleStyle);
        
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.serverName", userLocale),
                messageUtil.getMessage("reports.export.header.serverCode", userLocale),
                messageUtil.getMessage("csv.dashboard.metric.total.orders", userLocale),
                messageUtil.getMessage("csv.dashboard.metric.total.sales", userLocale),
                messageUtil.getMessage("reports.export.header.averageOrderValue", userLocale),
                messageUtil.getMessage("reports.export.header.totalTablesServed", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (salesByServerReport != null && salesByServerReport.getServers() != null) {
            for (com.gulfnet.shared_library.model.response.dto.PerformanceResponse.SalesByServerItem server : salesByServerReport.getServers()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(server.getServerName() != null ? server.getServerName() : "N/A");
                row.createCell(1).setCellValue(server.getServerCode() != null ? server.getServerCode() : "N/A");
                row.createCell(2).setCellValue(server.getTotalOrders() != null ? server.getTotalOrders() : 0);
                org.apache.poi.ss.usermodel.Cell salesCell = row.createCell(3);
                if (server.getTotalSales() != null) {
                    salesCell.setCellValue(CurrencyFormatter.formatAmount(server.getTotalSales(), currencySymbol).doubleValue());
                    salesCell.setCellStyle(monetaryStyle);
                }
                org.apache.poi.ss.usermodel.Cell avgOrderCell = row.createCell(4);
                if (server.getAverageOrderValue() != null) {
                    avgOrderCell.setCellValue(CurrencyFormatter.formatAmount(server.getAverageOrderValue(), currencySymbol).doubleValue());
                    avgOrderCell.setCellStyle(monetaryStyle);
                }
                row.createCell(5).setCellValue(server.getTotalTablesServed() != null ? server.getTotalTablesServed() : 0);
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates the “Customer Rating Distribution” sheet for scheduled performance exports.
     */
    private void createCustomerRatingSheetForSchedule(Workbook workbook,
            com.gulfnet.shared_library.model.response.dto.PerformanceResponse.CustomerRatingDistribution customerRatingDistribution,
            com.gulfnet.shared_library.entity.Restaurant restaurant,
            com.gulfnet.shared_library.entity.RestaurantGroup restaurantGroup,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle titleStyle,
            org.apache.poi.ss.usermodel.CellStyle percentageDecimalStyle,
            EmailSchedule schedule,
            Locale userLocale,
            MessageUtil messageUtil) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.customerRatingDistribution", userLocale));
        int rowNum = 0;
        rowNum = addReportInfoForSchedule(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime, schedule, userLocale, messageUtil);

        rowNum++;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.rating", userLocale),
                messageUtil.getMessage("reports.export.header.count", userLocale),
                messageUtil.getMessage("reports.export.header.percentage", userLocale)
        };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        if (customerRatingDistribution != null && customerRatingDistribution.getDistribution() != null) {
            for (com.gulfnet.shared_library.model.response.dto.PerformanceResponse.RatingDistributionItem rating : customerRatingDistribution.getDistribution()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rating.getRating() != null ? String.valueOf(rating.getRating()) : "N/A");
                row.createCell(1).setCellValue(rating.getCount() != null ? rating.getCount() : 0);
                org.apache.poi.ss.usermodel.Cell percentageCell = row.createCell(2);
                if (rating.getPercentage() != null) {
                    percentageCell.setCellValue(rating.getPercentage());
                    percentageCell.setCellStyle(percentageDecimalStyle);
                }
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String buildExportFilename(MessageUtil messageUtil, String messageKey, Locale locale, String extension) {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String baseName = sanitizeExportFilename(messageUtil.getMessage(messageKey, locale));
        return baseName + "_" + timestamp + extension;
    }

    private static String sanitizeExportFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "export";
        }
        return filename.trim().replaceAll("[<>:\"/\\\\|?*]", "_");
    }
}
