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

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  SecurityConfig — VERSION CORRIGÉE
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  RÈGLE D'OR Spring Security :                                       │
 *  │  Les règles sont évaluées dans l'ORDRE de déclaration.             │
 *  │  La PREMIÈRE qui correspond gagne — les suivantes sont ignorées.    │
 *  │  → Toujours déclarer du plus SPÉCIFIQUE au plus GÉNÉRAL.           │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 *  PROBLÈMES CORRIGÉS :
 *
 *  ❌ BUG 1 — Règle trop large avant les sous-chemins
 *     .requestMatchers("/api/production/**")          ← interceptait TOUT
 *     .requestMatchers("/api/production/archives/**") ← jamais atteinte
 *     → SOLUTION : déplacer le controller archives sur /api/production-archives
 *       et déclarer sa règle AVANT /api/production/**
 *
 *  ❌ BUG 2 — /api/production-archives/** absent du SecurityConfig
 *     Le nouveau controller n'était pas protégé → Spring retournait 403
 *     car anyRequest().authenticated() ne donnait pas les bons rôles.
 *     → SOLUTION : ajouter les règles explicites pour /api/production-archives/**
 *
 *  ❌ BUG 3 — Doublon inutile après règle large
 *     .requestMatchers("/api/production/**")              ← ligne 1 : tous rôles
 *     .requestMatchers("/api/production/archives/**")     ← ligne 2 : jamais évaluée
 *     La ligne 2 était dead code pur.
 *
 *  BONNE PRATIQUE — Ordre recommandé :
 *    1. PUBLIC  (permitAll)
 *    2. OPTIONS (CORS preflight)
 *    3. Règles SPÉCIFIQUES avec sous-chemins précis
 *    4. Règles GÉNÉRALES sur un préfixe large (/api/xxx/**)
 *    5. anyRequest().authenticated() EN DERNIER
 * ══════════════════════════════════════════════════════════════════════════
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

                        // ══════════════════════════════════════════════════════════
                        //  1. ACCÈS PUBLIC — Swagger + Auth
                        //     Toujours en premier, pas de token requis
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        .requestMatchers("/api/auth/**", "/auth/**").permitAll()

                        // Preflight CORS — doit être avant toute règle authentifiée
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ══════════════════════════════════════════════════════════
                        //  2. STOCK ERP
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/erp/stock/**",
                                "/web/erp/stock/**",
                                "/api/web/erp/stock/**"
                        ).hasAnyAuthority(
                                "ROLE_ADMIN","ROLE_MAGASINIER",
                                "ROLE_RESPONSABLE_MAGASIN","ROLE_CONSULTATION"
                        )

                        // ══════════════════════════════════════════════════════════
                        //  3. TRANSFERTS
                        //  ✅ Règles spécifiques AVANT la règle générale /api/transfers/**
                        // ══════════════════════════════════════════════════════════

                        // Sous-chemins spécifiques (dashboard, validate, cancel) — AVANT **
                        .requestMatchers(
                                "/api/transfers/dashboard",
                                "/api/transfers/validate/**",
                                "/api/transfers/cancel/**"
                        ).hasAnyAuthority("ROLE_ADMIN","ROLE_RESPONSABLE_MAGASIN")

                        // Export CSV — spécifique, doit précéder /api/transfers/archives/**
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/transfers/archives/export/csv"
                        ).hasAnyAuthority("ROLE_ADMIN","ROLE_RESPONSABLE_MAGASIN")

                        // Consultation archives transferts
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/transfers/archives/**"
                        ).authenticated()

                        // Création (POST) — règle méthode avant la règle générale
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/transfers/**"
                        ).hasAnyAuthority(
                                "ROLE_ADMIN","ROLE_MAGASINIER","ROLE_RESPONSABLE_MAGASIN"
                        )

                        // Consultation générale transferts — règle large EN DERNIER pour ce préfixe
                        .requestMatchers(HttpMethod.GET, "/api/transfers/**").authenticated()

                        // ══════════════════════════════════════════════════════════
                        //  4. MOUVEMENTS & ÉTIQUETTES
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers("/api/mouvements/**")
                        .hasAnyAuthority(
                                "ROLE_ADMIN","ROLE_MAGASINIER","ROLE_RESPONSABLE_MAGASIN"
                        )

                        .requestMatchers("/api/etiquette/**")
                        .hasAnyAuthority("ROLE_ADMIN","ROLE_MAGASINIER")

                        // ══════════════════════════════════════════════════════════
                        //  5. PRODUCTION ARCHIVES
                        //  ✅ FIX CRITIQUE — déclaré AVANT /api/production/**
                        //     Controller séparé sur /api/production-archives (tiret)
                        //     pour éviter tout conflit de préfixe avec /api/production/**
                        //
                        //  Ordre dans ce bloc :
                        //    a) POST /trigger  → ADMIN uniquement (action destructive)
                        //    b) GET  /**       → ADMIN + MANAGER  (consultation + DL)
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/production-archives/trigger"
                        ).hasAuthority("ROLE_ADMIN")

                        .requestMatchers(
                                "/api/production-archives/**"
                        ).hasAnyAuthority("ROLE_ADMIN","ROLE_MANAGER")

                        // ══════════════════════════════════════════════════════════
                        //  6. PRODUCTION LOGS (sorties)
                        //  ✅ Déclaré APRÈS les archives — plus general, ne risque plus
                        //     d'intercepter /api/production-archives/**
                        //     (qui a un préfixe différent de toute façon)
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers("/api/production/**")
                        .hasAnyAuthority(
                                "ROLE_ADMIN","ROLE_RESPONSABLE_MAGASIN","ROLE_MAGASINIER"
                        )

                        // ══════════════════════════════════════════════════════════
                        //  7. INVENTAIRE
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers("/api/inventory/**")
                        .hasAnyAuthority(
                                "ROLE_ADMIN","ROLE_RESPONSABLE_MAGASIN","ROLE_MAGASINIER"
                        )

                        // ══════════════════════════════════════════════════════════
                        //  8. MENU ITEMS
                        //  ✅ Règle GET (authenticated) AVANT la règle générale (ADMIN)
                        //     sinon le GET serait aussi bloqué à ADMIN
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers(HttpMethod.GET, "/api/menu-items/**").authenticated()
                        .requestMatchers("/api/menu-items/**").hasAuthority("ROLE_ADMIN")

                        // ══════════════════════════════════════════════════════════
                        //  9. AUDIT
                        //  ✅ /api/audit/archives/** AVANT /api/audit/**
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers("/api/audit/archives/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/audit/**").authenticated()

                        // ══════════════════════════════════════════════════════════
                        //  10. ADMINISTRATION & UTILISATEURS
                        // ══════════════════════════════════════════════════════════
                        .requestMatchers("/api/admin/**", "/admin/**")
                        .hasAuthority("ROLE_ADMIN")

                        .requestMatchers("/api/user/**")
                        .hasAnyAuthority(
                                "ROLE_ADMIN","ROLE_MAGASINIER",
                                "ROLE_RESPONSABLE_MAGASIN","ROLE_CONSULTATION"
                        )

                        // ══════════════════════════════════════════════════════════
                        //  11. DÉFAUT — TOUJOURS EN DERNIER
                        //  Tout ce qui n'est pas explicitement déclaré ci-dessus
                        //  doit être authentifié (pas permit, pas de rôle spécifique)
                        // ══════════════════════════════════════════════════════════
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Accepte toutes les origines (dev) — restreindre en production
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList(
                "GET","POST","PUT","DELETE","OPTIONS","PATCH"
        ));
        config.setAllowedHeaders(Arrays.asList(
                "Authorization","Content-Type","Accept","X-Requested-With"
        ));
        config.setAllowCredentials(true);
        // Content-Disposition exposé pour que le navigateur voie le nom du fichier téléchargé
        config.addExposedHeader("Content-Disposition");
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}