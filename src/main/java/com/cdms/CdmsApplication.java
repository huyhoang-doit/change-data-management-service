package com.cdms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CdmsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CdmsApplication.class, args);
	}

}
