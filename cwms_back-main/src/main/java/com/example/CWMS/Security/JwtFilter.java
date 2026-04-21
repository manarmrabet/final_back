package com.example.CWMS.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired private JwtUtils jwtUtils;
    @Autowired private UserDetailsService userDetailsService;

    /**
     * Optimisation : On ne filtre pas les requêtes d'authentification
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Gestion des requêtes CORS OPTIONS (pré-vol)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerAuth = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();

        // Logs de diagnostic
        System.out.println("=== JwtFilter === " + request.getMethod() + " " + requestURI);

        try {
            // 2. Vérification de la présence du Token
            if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
                String jwt = headerAuth.substring(7);

                // 3. Validation du Token
                if (jwtUtils.validateJwtToken(jwt)) {
                    String username = jwtUtils.getUserNameFromJwtToken(jwt);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // 4. Normalisation des Rôles (Ajout automatique de ROLE_ si absent)
                    // Indispensable pour que @PreAuthorize("hasRole('ADMIN')") fonctionne
                    List<SimpleGrantedAuthority> authorities = userDetails.getAuthorities().stream()
                            .map(auth -> {
                                String authority = auth.getAuthority();
                                if (!authority.startsWith("ROLE_")) {
                                    return new SimpleGrantedAuthority("ROLE_" + authority.toUpperCase());
                                }
                                return new SimpleGrantedAuthority(authority);
                            })
                            .collect(Collectors.toList());

                    // 5. Création de l'objet d'authentification
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

                    // 6. Injection dans le contexte de sécurité de Spring
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Logs de succès
                    System.out.println("✅ Authentification réussie pour : " + username);
                    System.out.println("✅ Rôles assignés : " + authorities);

                } else {
                    System.out.println("❌ Token invalide ou expiré pour l'URI : " + requestURI);
                }
            } else {
                // Log informatif (utile pour les endpoints publics qui ne sont pas /api/auth/)
                System.out.println("ℹ️ Aucun Bearer token trouvé pour l'URI : " + requestURI);
            }
        } catch (Exception e) {
            System.err.println("🔥 ERREUR CRITIQUE JwtFilter -> URI: " + requestURI + " | " + e.getMessage());
            // En cas d'erreur, on peut choisir de vider le contexte pour plus de sécurité
            SecurityContextHolder.clearContext();
        }

        // 7. Continuer la chaîne de filtres
        filterChain.doFilter(request, response);
    }
}