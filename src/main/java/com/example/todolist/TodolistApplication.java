package com.example.todolist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import tech.ailef.snapadmin.external.SnapAdminAutoConfiguration;

@SpringBootApplication
@ImportAutoConfiguration(SnapAdminAutoConfiguration.class)
@EnableScheduling
public class TodolistApplication {

	public static void main(String[] args) {
		SpringApplication.run(TodolistApplication.class, args);
	}

}
