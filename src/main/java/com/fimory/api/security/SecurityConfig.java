package com.fimory.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final HeaderAuthFilter headerAuthFilter;

    public SecurityConfig(HeaderAuthFilter headerAuthFilter) {
        this.headerAuthFilter = headerAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/health/db", "/storage/**").permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/movies/**",
                                "/api/stories/**",
                                "/api/reader/**",
                                "/api/categories/**",
                                "/api/search",
                                "/api/movies/search",
                                "/api/chatbot",
                                "/api/crawl/**",
                                "/api/me",
                                "/api/preferences",
                                "/api/user/preferences",
                                "/api/user/series-favorites",
                                "/api/notifications/**",
                                "/api/favorites/**",
                                "/api/history/**",
                                "/api/experience/**",
                                "/api/admin/categories/**",
                                "/api/admin/stats/movies/views",
                                "/api/videos/*/variants/**",
                                "/api/videos/*/segments/**",
                                "/api/videos/*/watermark",
                                "/videos/*/variants/**",
                                "/videos/*/segments/**",
                                "/videos/*/watermark"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"error\":\"Forbidden\"}");
                        }))
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
