package com.connecthub.media;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
@SpringBootApplication @EnableDiscoveryClient @EnableFeignClients
public class MediaServiceApplication { public static void main(String[] a) { SpringApplication.run(MediaServiceApplication.class, a); } }
