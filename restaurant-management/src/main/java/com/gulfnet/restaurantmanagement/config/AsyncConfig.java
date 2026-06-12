package com.gulfnet.restaurantmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Creates a custom thread pool executor for bulk upload tasks.
     * Configures a dedicated executor with specific pool size, queue capacity,
     * and graceful shutdown behavior for handling asynchronous bulk upload operations.
     *
     * @return Executor configured for bulk upload task execution
     */
    @Bean(name = "bulkUploadTaskExecutor")
    public Executor bulkUploadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("BulkUpload-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool used for asynchronous employee-assignment work (bean name
     * {@code employeeAssignmentTaskExecutor} for {@code @Async} or explicit injection).
     * Tuned for higher throughput than bulk upload: larger core/max pool and queue, same graceful
     * shutdown ({@code waitForTasksToCompleteOnShutdown} and {@code awaitTerminationSeconds = 60}).
     *
     * @return executor with {@code EmployeeAssign-} thread name prefix
     */
    @Bean(name = "employeeAssignmentTaskExecutor")
    public Executor employeeAssignmentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("EmployeeAssign-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool for best-effort notifications (FCM/KDS/waiter).
     * Keeps notification IO off the request thread and avoids ForkJoinPool contention.
     */
    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("Notif-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}