package com.connecthub.auth.config;

import com.connecthub.auth.config.oauth2.OAuth2AuthenticationFailureHandler;
import com.connecthub.auth.config.oauth2.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Mirrors api-gateway JwtAuthenticationFilter.OPEN_ENDPOINTS for /api/v1/auth (prefix match there). */
    static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/login/**", "/api/v1/auth/public/**",
        "/api/v1/auth/verify-registration-otp", "/api/v1/auth/resend-registration-otp",
        "/api/v1/auth/forgot-password", "/api/v1/auth/verify-reset-otp", "/api/v1/auth/verify-reset-otp/**",
        "/api/v1/auth/reset-password", "/api/v1/auth/refresh", "/api/v1/auth/validate",
        "/api/v1/auth/phone/request-otp", "/api/v1/auth/phone/verify-otp"
    };

    static boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "PLATFORM_ADMIN".equalsIgnoreCase(role);
    }

    private static boolean hasText(String v) { return v != null && !v.isBlank(); }

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints: exactly the set the gateway lets through without a JWT
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**",
                    "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Admin API: the gateway already enforces the role; enforce it here too so a request that
                // bypasses the gateway cannot use it (identity headers are only visible with the internal secret)
                .requestMatchers("/api/v1/auth/admin/**").access((authentication, ctx) ->
                    new AuthorizationDecision(isAdminRole(ctx.getRequest().getHeader("X-User-Role"))))
                // Everything else needs a caller identified by the gateway (X-User-Id)
                .anyRequest().access((authentication, ctx) ->
                    new AuthorizationDecision(hasText(ctx.getRequest().getHeader("X-User-Id"))))
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint ->
                    endpoint.baseUri("/oauth2/authorization"))
                .redirectionEndpoint(endpoint ->
                    endpoint.baseUri("/login/oauth2/code/*"))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
