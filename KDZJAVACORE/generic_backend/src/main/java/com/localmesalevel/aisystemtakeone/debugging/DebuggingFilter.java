package com.localmesalevel.aisystemtakeone.debugging;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DebuggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {
        System.out.println("[DEBUG] Incoming " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("[DEBUG] Origin=" + request.getHeader("Origin") + " Host=" + request.getHeader("Host"));
        try {
            filterChain.doFilter(request, response);
        } finally {
            System.out.println("[DEBUG] Outgoing " + request.getMethod() + " " + request.getRequestURI() + " status=" + response.getStatus());
        }
    }
}
