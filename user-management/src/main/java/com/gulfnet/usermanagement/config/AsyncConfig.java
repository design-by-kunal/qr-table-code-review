package com.gulfnet.usermanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Configures the {@link Executor} used for bulk upload asynchronous tasks.
     * Defines a thread pool with a small core size, limited max size,
     * and queue capacity suitable for background processing of bulk uploads.
     *
     * @return a configured {@link ThreadPoolTaskExecutor} bean named {@code bulkUploadTaskExecutor}
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
     * Isolated pool for transactional email follow-ups (e.g. forgot-password OTP) so SMTP latency
     * does not block HTTP threads or compete with bulk-upload workers.
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("AuthEmail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Override
    /**
     * Provides the default {@link Executor} for methods annotated with {@code @Async}.
     * Delegates to the configured {@code bulkUploadTaskExecutor}.
     *
     * @return the executor to be used for asynchronous method execution
     */
    public Executor getAsyncExecutor() {
        return bulkUploadTaskExecutor();
    }

    /**
     * Returns a handler for uncaught exceptions thrown from asynchronous methods,
     * logging the method name, parameters, and stack trace without rethrowing.
     *
     * @return an {@link AsyncUncaughtExceptionHandler} that logs uncaught async exceptions
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("Uncaught exception in async method: {} with parameters: {}", 
                    method.getName(), params, ex);
                // Log the exception but don't throw - the async method should handle its own errors
                // and update the BulkUpload status accordingly
            }
        };
    }
} 