package com.example.CWMS.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * MlRestConfig — VERSION FINALE
 *
 * ROOT CAUSE DU 422 "input:null" DEPUIS SPRING BOOT :
 *
 *   Uvicorn sans uvicorn[standard] ne supporte pas le
 *   "Transfer-Encoding: chunked" que RestTemplate utilise par défaut
 *   quand il ne connaît pas la taille du body à l'avance.
 *   Résultat : uvicorn reçoit le body en chunks → ne sait pas le lire
 *   → body = null → Pydantic : "input":null → 422.
 *
 *   Le test Python (urllib) fonctionnait car urllib calcule le
 *   Content-Length et l'envoie en une seule fois (pas de chunked).
 *
 * SOLUTION :
 *   BufferingClientHttpRequestFactory enveloppe SimpleClientHttpRequestFactory.
 *   Il bufferise le body ENTIER avant d'envoyer la requête.
 *   Cela permet à Spring Boot de calculer le Content-Length exact
 *   et d'envoyer la requête avec Content-Length plutôt qu'en chunked.
 *   Uvicorn reçoit un body complet → Pydantic le lit → 200 OK.
 */
@Configuration
public class MlRestConfig {

    @Value("${cwms.ml.base-url:http://localhost:8000}")
    private String mlBaseUrl;

    @Bean(name = "mlRestTemplate")
    public RestTemplate mlRestTemplate(RestTemplateBuilder builder) {

        // SimpleClientHttpRequestFactory avec timeouts
        SimpleClientHttpRequestFactory simpleFactory = new SimpleClientHttpRequestFactory();
        simpleFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        simpleFactory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        // BufferingClientHttpRequestFactory = bufferise le body entier
        // → calcule Content-Length → pas de chunked encoding → uvicorn OK
        BufferingClientHttpRequestFactory bufferingFactory =
                new BufferingClientHttpRequestFactory(simpleFactory);

        RestTemplate restTemplate = new RestTemplate(bufferingFactory);

        // Jackson : application/json SANS ;charset=UTF-8 dans le header
        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(new ObjectMapper());
        jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
        jacksonConverter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json")
        ));

        restTemplate.setMessageConverters(List.of(
                jacksonConverter,
                new StringHttpMessageConverter(StandardCharsets.UTF_8)
        ));

        // rootUri via UriTemplateHandler
        restTemplate.setUriTemplateHandler(
                new org.springframework.web.util.DefaultUriBuilderFactory(mlBaseUrl)
        );

        return restTemplate;
    }
}