package br.com.specvora_service.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final IdempotencyFilter idempotencyFilter;
    private final RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Endpoints Públicos (Swagger, OpenAPI e Autenticação/Registro)
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/auth/login",
                                "/auth/register"
                        ).permitAll()

                        // 2. Operações administrativas (CRUD de Veículos - POST, PUT, DELETE) exclusivas para ROLE_ADMIN
                        .requestMatchers(HttpMethod.POST, "/vehicles").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/vehicles/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/vehicles/**").hasRole("ADMIN")

                        // 3. Consultas e buscas de veículos permitidas para usuários autenticados (USER ou ADMIN)
                        .requestMatchers(HttpMethod.GET, "/vehicles/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/vehicles/search").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/auth/me").authenticated()

                        // Qualquer outra rota exige autenticação
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // Customização do retorno 401 Unauthorized
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("""
                                    {
                                        "status": 401,
                                        "error": "Unauthorized",
                                        "message": "Acesso não autorizado: credenciais ausentes ou token inválido"
                                    }
                                    """);
                        })
                        // Customização do retorno 403 Forbidden (RBAC - Role Based Access Control)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("""
                                    {
                                        "status": 403,
                                        "error": "Forbidden",
                                        "message": "Acesso proibido: seu perfil não possui permissão para executar esta operação"
                                    }
                                    """);
                        })
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, JwtAuthFilter.class)
                .addFilterAfter(rateLimitingFilter, IdempotencyFilter.class);

        return http.build();
    }
}
