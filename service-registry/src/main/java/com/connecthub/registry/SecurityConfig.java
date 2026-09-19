package com.connecthub.registry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The registry decides where the gateway sends traffic, so it must not accept anonymous registrations
 * (a rogue instance registered as "auth-service" would receive real logins). Every request needs HTTP basic
 * credentials (EUREKA_USER / EUREKA_PASSWORD, the same ones each client already carries in its service URL);
 * only health/info stay open so start-backend.ps1 and monitors can probe it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()) // Eureka clients POST/PUT/DELETE without a CSRF token
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .httpBasic(b -> {});
        return http.build();
    }

    @Bean
    public UserDetailsService eurekaUser(@Value("${EUREKA_USER:eureka}") String user,
                                         @Value("${EUREKA_PASSWORD:eureka}") String password) {
        return new InMemoryUserDetailsManager(User.builder()
                .username(user)
                .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password))
                .roles("EUREKA")
                .build());
    }
}
