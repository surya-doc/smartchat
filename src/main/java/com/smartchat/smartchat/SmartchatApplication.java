package com.smartchat.smartchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartchatApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartchatApplication.class, args);
	}

}
