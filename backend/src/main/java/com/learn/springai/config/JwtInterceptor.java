package com.learn.springai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.learn.springai.service.JwtService;

import io.jsonwebtoken.Claims;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight requests
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // Check if the path is explicitly public
        boolean isPublicPath = path.startsWith("/api/auth/") 
                || ("/api/users".equals(path) && HttpMethod.POST.name().equalsIgnoreCase(request.getMethod()))
                || "/api/conversations/trips/public".equals(path)
                || (path.startsWith("/api/conversations/trips/") && path.endsWith("/download"))
                || (path.startsWith("/api/conversations/trips/") && path.endsWith("/download-url"))
                || (path.startsWith("/api/conversations/trips/") && path.endsWith("/thumbnail"))
                || path.startsWith("/api/conversations/share/")
                || (path.startsWith("/api/conversations/") && path.endsWith("/destination-image"))
                || path.startsWith("/api/public-static/")
                || "/resources/".startsWith(path); // Static resources/uploads

        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = request.getParameter("token");
        }

        if (token != null && jwtService.validateAccessToken(token)) {
            Claims claims = jwtService.getClaimsFromToken(token);
            request.setAttribute("userId", claims.getSubject());
            request.setAttribute("userRole", claims.get("role", String.class));
            request.setAttribute("userEmail", claims.get("email", String.class));
            return true;
        }

        if (isPublicPath) {
            return true;
        }

        // Return 401 Unauthorized for private paths with invalid/missing token
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Unauthorized: Missing or invalid token");
        return false;
    }
}
