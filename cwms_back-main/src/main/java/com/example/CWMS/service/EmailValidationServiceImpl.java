package com.example.CWMS.service;

import com.example.CWMS.iservice.EmailValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Service
public class EmailValidationServiceImpl implements EmailValidationService {

    private static final Logger log = LoggerFactory.getLogger(EmailValidationServiceImpl.class);

    // ⏱️ Timeouts configurables — courts pour ne pas bloquer l'application
    private static final int DNS_TIMEOUT_MS  = 3000; // 3s pour la résolution MX
    private static final int SMTP_TIMEOUT_MS = 4000; // 4s pour le handshake SMTP
    private static final int SMTP_PORT       = 25;

    // Adresse expéditeur fictive pour le handshake SMTP
    private static final String SENDER_EMAIL = "verify@cwms-check.com";

    /**
     * Point d'entrée principal — seule méthode publique de cette classe.
     * @return true si l'email est joignable, false sinon
     */
    @Override
    public boolean isEmailReachable(String email) {
        if (email == null || !email.contains("@")) return false;

        String domain = email.substring(email.indexOf('@') + 1);

        // ÉTAPE 1 : Vérification MX Records
        List<String> mxServers = resolveMxRecords(domain);
        if (mxServers.isEmpty()) {
            log.warn("Aucun serveur MX trouvé pour le domaine : {}", domain);
            return false;
        }

        // ÉTAPE 2 : SMTP Handshake sur le premier serveur MX valide
        for (String mxHost : mxServers) {
            Boolean result = trySmtpHandshake(mxHost, email);
            if (result != null) {
                return result; // Réponse définitive obtenue
            }
            // Si null → serveur injoignable, on essaie le suivant
        }

        // Aucun serveur n'a répondu → on laisse passer par sécurité
        log.warn("Aucun serveur MX n'a répondu pour {} — validation ignorée (fail-open)", email);
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVÉ — détails d'implémentation non exposés dans l'interface
    // ─────────────────────────────────────────────────────────────

    /**
     * Résout les enregistrements MX d'un domaine via DNS.
     */
    private List<String> resolveMxRecords(String domain) {
        List<String> mxHosts = new ArrayList<>();
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(DNS_TIMEOUT_MS));
            env.put("com.sun.jndi.dns.timeout.retries", "1");

            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mxAttr = attrs.get("MX");

            if (mxAttr != null) {
                for (int i = 0; i < mxAttr.size(); i++) {
                    String record = mxAttr.get(i).toString();
                    // Format: "10 alt1.gmail-smtp-in.l.google.com."
                    String[] parts = record.split("\\s+");
                    if (parts.length >= 2) {
                        String host = parts[1];
                        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
                        mxHosts.add(host);
                        log.debug("MX Record trouvé pour {} : {}", domain, host);
                    }
                }
            }
        } catch (NamingException e) {
            log.warn("Erreur DNS pour le domaine {} : {}", domain, e.getMessage());
        }
        return mxHosts;
    }

    /**
     * Tente un handshake SMTP (EHLO → MAIL FROM → RCPT TO) pour vérifier l'adresse.
     *
     * @return Boolean.TRUE  → adresse acceptée par le serveur
     *         Boolean.FALSE → adresse rejetée (550/551/553...)
     *         null          → serveur injoignable (timeout, port bloqué)
     */
    // Dans EmailValidationServiceImpl.java
// Aucune modification de logique — juste suppression du warning

    private Boolean trySmtpHandshake(String mxHost, String recipientEmail) {
        log.debug("Tentative SMTP sur {} pour {}", mxHost, recipientEmail);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(mxHost, SMTP_PORT), SMTP_TIMEOUT_MS);
            socket.setSoTimeout(SMTP_TIMEOUT_MS);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(),
                            StandardCharsets.UTF_8));          // ✅ FIX Internationalization
                 PrintWriter writer = new PrintWriter(
                         new OutputStreamWriter(socket.getOutputStream(),
                                 StandardCharsets.UTF_8), true)) { // ✅ FIX Internationalization

                String banner = reader.readLine();
                if (banner == null || !banner.startsWith("220")) {
                    log.warn("Bannière SMTP invalide sur {} : {}", mxHost, banner);
                    return null; // ✅ null intentionnel — serveur injoignable
                }

                writer.println("EHLO cwms-check.com");
                String ehloResponse = readSmtpResponse(reader);
                if (!ehloResponse.startsWith("2")) return null;

                writer.println("MAIL FROM:<" + SENDER_EMAIL + ">");
                String mailFromResponse = readSmtpResponse(reader);
                if (!mailFromResponse.startsWith("2")) return null;

                writer.println("RCPT TO:<" + recipientEmail + ">");
                String rcptResponse = readSmtpResponse(reader);

                writer.println("QUIT");

                log.info("Réponse RCPT TO pour {} sur {} : {}",
                        recipientEmail, mxHost, rcptResponse);

                if (rcptResponse.startsWith("2")) return Boolean.TRUE;
                if (rcptResponse.startsWith("55") || rcptResponse.startsWith("5.1")) {
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            }

        } catch (java.net.SocketTimeoutException e) {
            log.warn("Timeout SMTP sur {} après {}ms", mxHost, SMTP_TIMEOUT_MS);
            return null;
        } catch (java.net.ConnectException e) {
            log.warn("Connexion refusée sur {}:{}", mxHost, SMTP_PORT);
            return null;
        } catch (Exception e) {
            log.warn("Erreur SMTP inattendue sur {} : {}", mxHost, e.getMessage());
            return null;
        }
    }

    /**
     * Lit une réponse SMTP multi-lignes.
     * Une réponse se termine quand le 4ème caractère est un espace (pas un tiret).
     * Ex: "250-SIZE 35882577" (continue) vs "250 OK" (fin)
     */
    private String readSmtpResponse(BufferedReader reader) throws Exception {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
        }
        return response.toString().trim();
    }
}