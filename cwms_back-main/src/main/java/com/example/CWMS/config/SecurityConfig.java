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

/**
 * SecurityConfig — version mise à jour avec routes transfert + ERP.
 *
 * Règles de sécurité transfert :
 *   - Créer un transfert   : MAGASINIER, RESPONSABLE_MAGASIN, ADMIN
 *   - Valider / Annuler    : RESPONSABLE_MAGASIN, ADMIN seulement
 *   - Consulter transferts : tous les rôles authentifiés
 *   - Données ERP          : tous les rôles authentifiés
 *   - Dashboard            : RESPONSABLE_MAGASIN, ADMIN
 */
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

                        // ── Auth publique ────────────────────────────────
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── Audit ────────────────────────────────────────
                        .requestMatchers("/api/audit/**").authenticated()

                        // ── Menu ─────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/api/menu-items/**").authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/menu-items/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/menu-items/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/menu-items/**").hasAuthority("ROLE_ADMIN")

                        // ── Admin ────────────────────────────────────────
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")

                        // ── Users ────────────────────────────────────────
                        .requestMatchers("/api/user/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MAGASINIER",
                                "ROLE_RESPONSABLE_MAGASIN", "ROLE_CONSULTATION")

                        .requestMatchers(HttpMethod.GET, "/api/erp/**").authenticated()
                        .requestMatchers("/api/transfers/dashboard").hasAnyAuthority("ROLE_ADMIN", "ROLE_RESPONSABLE_MAGASIN")
                        .requestMatchers(HttpMethod.PUT, "/api/transfers/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_RESPONSABLE_MAGASIN")
                        .requestMatchers(HttpMethod.POST, "/api/transfers/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MAGASINIER", "ROLE_RESPONSABLE_MAGASIN")
                        .requestMatchers(HttpMethod.GET, "/api/transfers/**").authenticated()
                        // === Important : autoriser aussi sans /api (ton Angular appelle sans /api) ===
                        .requestMatchers(HttpMethod.GET, "/transfers/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/transfers/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MAGASINIER", "ROLE_RESPONSABLE_MAGASIN")
                        .requestMatchers(HttpMethod.PUT, "/transfers/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_RESPONSABLE_MAGASIN")
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Web Angular + Mobile (émulateur Android = 10.0.2.2)
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",
                "http://10.0.2.2:8080"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
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