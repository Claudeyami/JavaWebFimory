package com.fimory.api.security;

import com.fimory.api.domain.UserEntity;
import com.fimory.api.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    private static final String USER_EMAIL_HEADER = "x-user-email";
    private final UserRepository userRepository;

    public HeaderAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String email = normalizeEmail(request.getHeader(USER_EMAIL_HEADER));
        if (email == null) {
            email = normalizeEmail(request.getParameter("email"));
        }
        if (email != null && !email.isBlank()) {
            userRepository.findByEmailIgnoreCase(email)
                    .ifPresent(user -> setAuthentication(user, request));
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(UserEntity user, HttpServletRequest request) {
        String role = user.getRole() != null ? user.getRole().getName() : "USER";
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                role,
                List.of()
        );

        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        );
        token.setDetails(new HttpHeaders());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }
}
