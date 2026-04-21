package com.example.CWMS.controller;

import com.example.CWMS.service.EtiquettePdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EtiquetteController {

    private final EtiquettePdfService service;

    /**
     * Route exacte du rapport §2.1 :
     * GET /Etiquettepdf/{orderEtiquette}/{start}/{end}/{username}
     */
    @GetMapping("/Etiquettepdf/{orderEtiquette}/{start}/{end}/{username}")
    public ResponseEntity<?> generate(
            @PathVariable String orderEtiquette,
            @PathVariable int    start,
            @PathVariable int    end,
            @PathVariable String username) {

        try {
            log.info("[Controller] Etiquette RCNO={} start={} end={} user={}",
                    orderEtiquette, start, end, username);

            byte[] pdf = service.generate(orderEtiquette, start, end, username);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"Etiquettes_" + orderEtiquette + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdf.length)
                    .body(pdf);

        } catch (Exception e) {
            // Log full stack trace
            log.error("[Controller] ERREUR COMPLETE:", e);

            // Remonter la cause racine
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("ERREUR: " + root.getClass().getSimpleName()
                            + " — " + root.getMessage());
        }
    }
}
