package com.learn.springai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to restrict access to Spring Boot Actuator endpoints (/actuator/**).
 * Only direct requests originating from localhost (127.0.0.1 / ::1 / 0:0:0:0:0:0:0:1)
 * without reverse proxy headers (X-Forwarded-For) are allowed.
 */
@Component
@Order(1)
@Slf4j
public class ActuatorLocalhostFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        if (requestURI.startsWith("/actuator")) {
            String remoteAddr = request.getRemoteAddr();
            String forwardedFor = request.getHeader("X-Forwarded-For");

            // If forwarded by an external proxy (like Nginx), or client IP is not local loopback
            boolean isForwarded = forwardedFor != null && !forwardedFor.isBlank();
            boolean isLocalIp = "127.0.0.1".equals(remoteAddr)
                             || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                             || "::1".equals(remoteAddr)
                             || "localhost".equalsIgnoreCase(request.getServerName());

            if (isForwarded || !isLocalIp) {
                log.warn("Blocked non-localhost attempt to access actuator endpoint '{}' from remote IP: {} (X-Forwarded-For: {})",
                        requestURI, remoteAddr, forwardedFor);
                response.sendError(HttpStatus.FORBIDDEN.value(), "Access to actuator endpoints is restricted to localhost only.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
