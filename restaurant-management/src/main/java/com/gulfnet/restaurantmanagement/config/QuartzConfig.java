/*package com.gulfnet.restaurantmanagement.config;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory; // Use this instead

import javax.sql.DataSource;

@Configuration
@Profile("!test")
public class QuartzConfig {

    @Autowired
    private DataSource dataSource;

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setQuartzProperties(quartzProperties());
        factory.setAutoStartup(true);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setOverwriteExistingJobs(true);
        factory.setApplicationContextSchedulerContextKey("applicationContext");
        
        // Use Spring's built-in SpringBeanJobFactory
        factory.setJobFactory(springJobFactory());
        
        return factory;
    }

    @Bean
    public Scheduler scheduler(SchedulerFactoryBean schedulerFactoryBean) throws SchedulerException {
        return schedulerFactoryBean.getScheduler();
    }

    @Bean
    public SpringBeanJobFactory springJobFactory() {
        return new SpringBeanJobFactory();
    }

    private java.util.Properties quartzProperties() {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "MenuPublishScheduler");
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.threadPool.threadCount", "10");
        properties.setProperty("org.quartz.threadPool.threadPriority", "5");
        properties.setProperty("org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread", "true");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        properties.setProperty("org.quartz.jobStore.useProperties", "false");
        properties.setProperty("org.quartz.jobStore.dataSource", "quartzDataSource");
        properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        properties.setProperty("org.quartz.jobStore.isClustered", "false");
        properties.setProperty("org.quartz.dataSource.quartzDataSource.driver", "org.postgresql.Driver");
        properties.setProperty("org.quartz.dataSource.quartzDataSource.URL", "jdbc:postgresql://rds-qrtable-dev.c1seyuksugkj.ap-southeast-1.rds.amazonaws.com:5432/qr_table_order_management");
        properties.setProperty("org.quartz.dataSource.quartzDataSource.user", "postgres");
        properties.setProperty("org.quartz.dataSource.quartzDataSource.password", "postgres");
        properties.setProperty("org.quartz.dataSource.quartzDataSource.maxConnections", "10");
        properties.setProperty("org.quartz.dataSource.quartzDataSource.validationQuery", "SELECT 1");
        properties.setProperty("org.quartz.scheduler.timeZone", "Asia/Kolkata");
        return properties;
    }

}*/

