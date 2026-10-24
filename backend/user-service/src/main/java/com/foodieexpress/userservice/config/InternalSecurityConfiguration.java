package com.foodieexpress.userservice.config;

import com.foodieexpress.userservice.dto.ApiError;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class InternalSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Qualifier("internalOrderJwtDecoder") JwtDecoder internalJwtDecoder) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                objectMapper, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                objectMapper, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "Access is denied")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/internal/users/*/addresses/*")
                                .hasAuthority("SCOPE_user:address:read")
                        .anyRequest().denyAll());
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt
                        .decoder(internalJwtDecoder)
                        .jwtAuthenticationConverter(internalAuthentication())));
        return http.build();
    }

    private JwtAuthenticationConverter internalAuthentication() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object scopes = jwt.getClaims().get("scopes");
            if (!(scopes instanceof List<?> values)) {
                return List.of();
            }
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .map(org.springframework.security.core.GrantedAuthority.class::cast)
                    .toList();
        });
        return converter;
    }

    private void writeError(
            ObjectMapper objectMapper,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), code, message, List.of()));
    }
}
