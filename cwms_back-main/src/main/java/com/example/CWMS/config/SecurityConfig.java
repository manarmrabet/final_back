package com.example.CWMS.config;

import com.example.CWMS.Security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // ── Auth publique ────────────────────────────────────────────
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── ERP data (lecture) — tous les rôles authentifiés ─────────
                        // Une seule règle, pas de contradictions

                        .requestMatchers(HttpMethod.GET, "/api/transfers/erp/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MAGASINIER",
                                "ROLE_RESPONSABLE_MAGASIN", "ROLE_CONSULTATION")

                        // ── Transferts — dashboard ────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/transfers/dashboard").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_RESPONSABLE_MAGASIN")

                        // ── Transferts — créer / batch ────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/transfers/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MAGASINIER", "ROLE_RESPONSABLE_MAGASIN")

                        // ── Transferts — valider / annuler ────────────────────────────
                        .requestMatchers(HttpMethod.PUT, "/api/transfers/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_RESPONSABLE_MAGASIN")

                        // ── Transferts — consulter ────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/transfers/**").authenticated()

                        // ── Audit ────────────────────────────────────────────────────
                        .requestMatchers("/api/audit/**").authenticated()

                        // ── Menu ─────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/api/menu-items/**").authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/menu-items/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/menu-items/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/menu-items/**").hasAuthority("ROLE_ADMIN")

                        // ── Admin ────────────────────────────────────────────────────
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")

                        // ── Users ────────────────────────────────────────────────────
                        .requestMatchers("/api/user/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MAGASINIER",
                                "ROLE_RESPONSABLE_MAGASIN", "ROLE_CONSULTATION")

                        // ── Tout le reste ─────────────────────────────────────────────
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",
                "http://10.0.2.2:8080"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}