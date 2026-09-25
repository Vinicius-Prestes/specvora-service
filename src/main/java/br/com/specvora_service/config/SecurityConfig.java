package br.com.specvora_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final IdempotencyFilter idempotencyFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final ErrorResponseWriter errorResponseWriter;

    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMINISTRADOR > ROLE_GESTOR
                ROLE_GESTOR > ROLE_USER
                ROLE_ADMIN > ROLE_USER
                ROLE_ADMINISTRADOR > ROLE_ADMIN
                """);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Hardening de Cabeçalhos HTTP de Segurança (OWASP Secure Headers)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions.policy("geolocation=(), microphone=(), camera=()"))
                )
                .authorizeHttpRequests(auth -> auth
                        // 1. Endpoints Públicos
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/auth/login",
                                "/auth/register"
                        ).permitAll()

                        // 2. Operações de exclusão (Exclusivas para ADMINISTRADOR)
                        .requestMatchers(HttpMethod.DELETE, "/vehicles/**").hasAnyRole("ADMINISTRADOR", "ADMIN")

                        // 3. Operações de escrita/modificação (GESTOR e ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, "/vehicles").hasAnyRole("GESTOR", "ADMINISTRADOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/vehicles/**").hasAnyRole("GESTOR", "ADMINISTRADOR", "ADMIN")

                        // 4. Consultas e leituras (USER, GESTOR e ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/vehicles/**").hasAnyRole("USER", "GESTOR", "ADMINISTRADOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/vehicles/search").hasAnyRole("USER", "GESTOR", "ADMINISTRADOR", "ADMIN")
                        .requestMatchers("/auth/me").authenticated()

                        // Demais rotas exigem autenticação
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                errorResponseWriter.write(response, request, HttpStatus.UNAUTHORIZED,
                                        "Acesso não autorizado: credenciais ausentes ou token inválido"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorResponseWriter.write(response, request, HttpStatus.FORBIDDEN,
                                        "Acesso proibido: seu perfil não possui permissão para executar esta operação"))
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, JwtAuthFilter.class)
                .addFilterAfter(rateLimitingFilter, IdempotencyFilter.class);

        return http.build();
    }

    // Evita registro duplicado dos filtros customizados no container servlet do Spring Boot
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> disableJwtRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> disableIdempotencyRegistration(IdempotencyFilter filter) {
        FilterRegistrationBean<IdempotencyFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> disableRateLimitingRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
