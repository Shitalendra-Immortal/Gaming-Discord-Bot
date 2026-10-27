package com.gamingbot.dashboard;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Dashboard login: when DASHBOARD_ADMIN_TOKEN is set, every request must
 * carry ?token=<token>. Without the token set, the dashboard stays open
 * (development only) and a warning is printed at startup.
 */
@Component
public class DashboardAuthFilter implements Filter {
    private final String adminToken = System.getenv("DASHBOARD_ADMIN_TOKEN");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (adminToken == null || adminToken.isBlank()) {
            System.out.println("WARNING: DASHBOARD_ADMIN_TOKEN is not set; the dashboard is open.");
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (adminToken.equals(httpRequest.getParameter("token"))) {
            chain.doFilter(request, response);
        } else {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Dashboard admin token required (?token=...)");
        }
    }
}
