package com.usic.uniFex.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import com.usic.uniFex.security.JwtAuthFilter;
import com.usic.uniFex.security.JwtService;
import com.usic.uniFex.security.MantenimientoApiFilter;
import com.usic.uniFex.model.service.MantenimientoService;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ORIGENES_POR_DEFECTO =
            "https://localhost,capacitor://localhost,"
            + "http://localhost:5173,http://127.0.0.1:5173,"
            + "http://localhost:5174,http://127.0.0.1:5174,"
            + "http://localhost:7676,http://127.0.0.1:7676";

    @Value("${unifex.cors.origenes:" + ORIGENES_POR_DEFECTO + "}")
    private String origenesCors;

    @Bean
    public org.springframework.web.filter.ForwardedHeaderFilter forwardedHeaderFilter() {
        return new org.springframework.web.filter.ForwardedHeaderFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsApi() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(origenesCors.split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Origen", "If-None-Match"));
        config.setExposedHeaders(List.of("ETag"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtService jwtService,
                                                      MantenimientoService mantenimientoService) throws Exception {
        http
                .securityMatcher(new OrRequestMatcher(
                        AntPathRequestMatcher.antMatcher("/api/auth/**"),
                        AntPathRequestMatcher.antMatcher("/api/app/**"),
                        AntPathRequestMatcher.antMatcher("/api/publico/**")))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsApi()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/api/auth/**")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/api/publico/**")).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"ok\":false,\"mensaje\":\"No autenticado\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"ok\":false,\"mensaje\":\"Sin permiso\"}");
                        }))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MantenimientoApiFilter(mantenimientoService), JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                auth -> auth.requestMatchers(
                        "/",
                        "/error",
                        "/credencial/**",
                        "/app/**",
                        "/control-responsable",
                        "/buscar-responsable",
                        "/vistaR/**",
                        "/admin/**",
                        "/iniciar-sesion/**",
                        "/acceso/**",
                        "/ver/**",
                        "/api/**",
                        "/venta/**",
                        "/guardar/**",
                        "/actualizar/**",
                        "/boletos/**",
                        "/files/**",
                        "/inscripciones/**",
                        "/ws/**",
                        "/app/**",
                        "/cerrar_sesion",
                        "/administracion/**",
                        "/vistaGenerarCredenciales/**",
                        "/assets/**").permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(login -> login
                        .loginPage("/")
                        .permitAll())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

}
