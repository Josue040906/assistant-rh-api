package com.assistantrh.assistant_rh_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssistantRhApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AssistantRhApiApplication.class, args);
	}

}
