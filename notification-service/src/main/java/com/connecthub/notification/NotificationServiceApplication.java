package com.connecthub.notification;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableDiscoveryClient @EnableAsync @EnableScheduling
public class NotificationServiceApplication { public static void main(String[] a) { SpringApplication.run(NotificationServiceApplication.class, a); } }
