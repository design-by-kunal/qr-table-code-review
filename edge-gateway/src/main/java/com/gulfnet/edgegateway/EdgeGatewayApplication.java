package com.gulfnet.edgegateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(scanBasePackages = {
    "com.gulfnet.edgegateway",
    "com.gulfnet.shared_library"
}, exclude = {DataSourceAutoConfiguration.class})
public class EdgeGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(EdgeGatewayApplication.class, args);
	}

}
