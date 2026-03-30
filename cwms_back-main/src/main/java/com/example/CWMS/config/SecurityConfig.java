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
import java.util.List;

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
                        // 1. Routes Publiques (Authentification)
                        .requestMatchers("/api/auth/**", "/auth/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 2. STOCK ERP (Lecture / Consultation) - Accessible à tous les rôles authentifiés
                        .requestMatchers(HttpMethod.GET, "/api/erp/stock/**", "/web/erp/stock/**", "/api/web/erp/stock/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_MAGASINIER", "ROLE_RESPONSABLE_MAGASIN", "ROLE_CONSULTATION")

                        // 3. TRANSFERTS (Logic métier spécifique)
                        // Dashboard et Validation (Actions sensibles)
                        .requestMatchers("/api/transfers/dashboard", "/api/transfers/validate/**", "/api/transfers/cancel/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_RESPONSABLE_MAGASIN")

                        // Création et Batch (Mobile/Flutter)
                        .requestMatchers(HttpMethod.POST, "/api/transfers/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_MAGASINIER", "ROLE_RESPONSABLE_MAGASIN")

                        // Consultation des transferts (Historique)
                        .requestMatchers(HttpMethod.GET, "/api/transfers/**")
                        .authenticated()

                        // 4. MOUVEMENTS DE STOCK (Entrée/Sortie/Ajustement)
                        .requestMatchers("/api/mouvements/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_MAGASINIER", "ROLE_RESPONSABLE_MAGASIN")

                        // 5. MENU ITEMS & AUDIT
                        .requestMatchers(HttpMethod.GET, "/api/menu-items/**").authenticated()
                        .requestMatchers("/api/menu-items/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/audit/**").authenticated()

                        // 6. ADMINISTRATION & UTILISATEURS
                        .requestMatchers("/api/admin/**", "/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/user/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MAGASINIER", "ROLE_RESPONSABLE_MAGASIN", "ROLE_CONSULTATION")

                        // 7. Sécurité par défaut
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Configuration large pour le développement (Mobile + Web)
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization")); // Important pour récupérer le token si besoin

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}