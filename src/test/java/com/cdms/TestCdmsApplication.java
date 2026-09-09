package com.cdms;

import org.springframework.boot.SpringApplication;

public class TestCdmsApplication {

	public static void main(String[] args) {
		SpringApplication.from(CdmsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
