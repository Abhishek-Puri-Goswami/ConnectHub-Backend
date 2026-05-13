package com.connecthub.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * ConnectHub Config Server — centralises all microservice configuration.
 * Reads from the private Git repository and serves config over HTTP.
 * Secured with HTTP Basic Auth (CONFIG_SERVER_USER / CONFIG_SERVER_PASSWORD).
 * Dashboard: http://localhost:8888/<service-name>/default
 */
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
