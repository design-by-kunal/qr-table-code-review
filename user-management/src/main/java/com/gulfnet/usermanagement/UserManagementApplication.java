package com.gulfnet.usermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.gulfnet.shared_library.repository")
@EntityScan(basePackages = "com.gulfnet.shared_library.entity")
@ComponentScan(basePackages = {"com.gulfnet.usermanagement", "com.gulfnet.shared_library"})
@EnableAsync
@EnableScheduling
public class UserManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserManagementApplication.class, args);
	}

}
