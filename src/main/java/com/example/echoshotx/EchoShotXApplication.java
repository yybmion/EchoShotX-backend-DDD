package com.example.echoshotx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Enable scheduled tasks for notification retry
@EnableAsync       // Enable async event processing
public class EchoShotXApplication {

	public static void main(String[] args) {
		SpringApplication.run(EchoShotXApplication.class, args);
	}

}
