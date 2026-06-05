package com.seoulchonnom.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.seoulchonnom")
public class SlcnappApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlcnappApplication.class, args);
	}

}