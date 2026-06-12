package com.gulfnet.integrationmanagement;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;


@SpringBootApplication
@EnableRabbit


@ComponentScan(basePackages = {"com.gulfnet.integrationmanagement", "com.gulfnet.shared_library"})
public class IntegrationManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntegrationManagementApplication.class, args);
	}

}
