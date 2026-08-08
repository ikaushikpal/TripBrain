package com.learn.springai.config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.learn.springai.service.JwtService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security JWT Authentication Filter.
 * Validates access tokens and populates SecurityContextHolder with user authority (ROLE_USER / ROLE_ADMIN)
 * as well as request attributes for backward compatibility.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = request.getParameter("token");
        }

        if (token != null && jwtService.validateAccessToken(token)) {
            try {
                Claims claims = jwtService.getClaimsFromToken(token);
                String userId = claims.getSubject();
                String email = claims.get("email", String.class);
                String rawRole = claims.get("role", String.class);
                if (rawRole == null || rawRole.isBlank()) {
                    rawRole = "USER";
                }

                String cleanRole = rawRole.replace("ROLE_", "").toUpperCase();
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + cleanRole),
                        new SimpleGrantedAuthority(cleanRole)
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Populate request attributes for backward compatibility with controllers
                request.setAttribute("userId", userId);
                request.setAttribute("userRole", rawRole);
                request.setAttribute("userEmail", email);
            } catch (Exception e) {
                log.warn("Failed to process JWT token: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
