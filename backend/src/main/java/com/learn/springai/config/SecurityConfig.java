package com.learn.springai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.learn.springai.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
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
    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .map(user -> {
                    String roleName = user.getRole().startsWith("ROLE_") ? user.getRole().toUpperCase() : "ROLE_" + user.getRole().toUpperCase();
                    return new org.springframework.security.core.userdetails.User(
                            user.getEmail(),
                            user.getPassword() != null ? user.getPassword() : "",
                            Collections.singletonList(new SimpleGrantedAuthority(roleName))
                    );
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }

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

                        // Public Actuator Health Check & Info Endpoints (for Blue-Green Deployer & Container Health Checks)
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                        // Sensitive Actuator Endpoints - Restrict to Localhost/Internal/Docker & Spring Boot Admin (spring.cloud1.mooo.com)
                        .requestMatchers("/actuator/**").access((authentication, context) -> {
                            HttpServletRequest request = context.getRequest();
                            String remoteAddr = request.getRemoteAddr();
                            String serverName = request.getServerName();

                            boolean isAllowedSource = "127.0.0.1".equals(remoteAddr)
                                    || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                                    || "::1".equals(remoteAddr)
                                    || (remoteAddr != null && (remoteAddr.startsWith("172.") || remoteAddr.startsWith("10.") || remoteAddr.startsWith("192.168.")))
                                    || "localhost".equalsIgnoreCase(serverName)
                                    || "spring.cloud1.mooo.com".equalsIgnoreCase(serverName)
                                    || "netdata.cloud1.mooo.com".equalsIgnoreCase(serverName);

                            return new org.springframework.security.authorization.AuthorizationDecision(isAllowedSource);
                        })

                        // Admin & Management Endpoints
                        .requestMatchers("/api/admin/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN", "USER", "ROLE_USER")

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
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition", "X-Admin-Requester-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
