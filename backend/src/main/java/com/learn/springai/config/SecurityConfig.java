package com.learn.springai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Consolidated Spring Security Configuration.
 * Manages SecurityFilterChain, Stateless JWT session policies, CORS,
 * public asset permissions, Actuator perimeter defense, and Role-Based
 * Authorization (RBAC).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Allow CORS OPTIONS pre-flight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Static Web & Frontend Assets
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/*.js",
                                "/**/*.js",
                                "/*.css",
                                "/**/*.css",
                                "/assets/**",
                                "/resources/**",
                                "/api/public-static/**")
                        .permitAll()
                        .requestMatchers(org.springframework.boot.autoconfigure.security.servlet.PathRequest
                                .toStaticResources().atCommonLocations())
                        .permitAll()

                        // Public Auth & Registration Endpoints
                        .requestMatchers("/api/auth/**").permitAll()

                        // Public Trip Sharing & Gallery Endpoints
                        .requestMatchers(
                                "/api/conversations/trips/public",
                                "/api/conversations/share/**",
                                "/api/conversations/trips/*/download",
                                "/api/conversations/trips/*/download-url",
                                "/api/conversations/trips/*/thumbnail",
                                "/api/conversations/*/destination-image")
                        .permitAll()

                        // Actuator Endpoints - Restrict strictly to Localhost without reverse-proxy
                        // headers
                        .requestMatchers("/actuator/**").access((authentication, context) -> {
                            HttpServletRequest request = context.getRequest();
                            String remoteAddr = request.getRemoteAddr();
                            String forwardedFor = request.getHeader("X-Forwarded-For");

                            boolean isForwarded = forwardedFor != null && !forwardedFor.isBlank();
                            boolean isLocalIp = "127.0.0.1".equals(remoteAddr)
                                    || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                                    || "::1".equals(remoteAddr)
                                    || "localhost".equalsIgnoreCase(request.getServerName());

                            return new org.springframework.security.authorization.AuthorizationDecision(
                                    !isForwarded && isLocalIp);
                        })

                        // Admin-only Endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Authenticated User Endpoints
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")

                        // Client-side SPA routes (forwarded to index.html)
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
