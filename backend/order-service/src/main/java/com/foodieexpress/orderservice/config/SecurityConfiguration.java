package com.foodieexpress.orderservice.config;

import com.foodieexpress.orderservice.dto.ApiError;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfiguration {

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ObjectMapper objectMapper, @Qualifier("jwtDecoder") JwtDecoder userJwtDecoder)
            throws Exception {
        http
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
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/my-orders").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/admin/orders").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/cancel").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/orders/restaurant/*").hasRole("RESTAURANT_OWNER")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/restaurant-status")
                                .hasRole("RESTAURANT_OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/delivery-provisioning/retry")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/admin/orders/*/cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders/*").hasRole("CUSTOMER")
                        .anyRequest().authenticated());
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .authenticationEntryPoint((request, response, exception) -> writeError(
                        objectMapper, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Authentication is required"))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        .decoder(userJwtDecoder)));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::authorities);
        return converter;
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                    .filter(role -> role.matches("[A-Z_]+"))
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(GrantedAuthority.class::cast)
                    .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String configuredOrigins) {
        List<String> allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain explicit origins and cannot contain '*'");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
