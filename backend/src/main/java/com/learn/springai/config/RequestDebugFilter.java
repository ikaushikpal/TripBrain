package com.learn.springai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestDebugFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().endsWith(".js")) {
            System.out.println("URI      : " + request.getRequestURI());
            System.out.println("Host     : " + request.getHeader("Host"));
            System.out.println("Origin   : " + request.getHeader("Origin"));
            System.out.println("Referer  : " + request.getHeader("Referer"));
            System.out.println("Scheme   : " + request.getScheme());
            System.out.println("Server   : " + request.getServerName());
            System.out.println("Port     : " + request.getServerPort());
            System.out.println("-------------------------");
        }

        filterChain.doFilter(request, response);
    }
}
