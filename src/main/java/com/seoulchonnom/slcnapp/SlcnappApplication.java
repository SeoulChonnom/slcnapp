package com.seoulchonnom.slcnapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class SlcnappApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlcnappApplication.class, args);
	}

}
