package io.coincraft.x402.common;

import io.coincraft.x402.support.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-API-Key";

    private final SecurityProperties securityProperties;

    public ApiKeyAuthenticationFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("GET".equals(method) && "/x402/protected/report".equals(path)) {
            return true;
        }
        if (path.startsWith("/actuator/health") || "/actuator/prometheus".equals(path)) {
            return true;
        }
        return !path.startsWith("/x402/payment-intents");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String presentedApiKey = request.getHeader(API_KEY_HEADER);
        if (!securityProperties.apiKey().equals(presentedApiKey)) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"status":401,"message":"API key authentication failed"}
                    """);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "x402-api-client",
                presentedApiKey,
                AuthorityUtils.createAuthorityList("ROLE_API")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
