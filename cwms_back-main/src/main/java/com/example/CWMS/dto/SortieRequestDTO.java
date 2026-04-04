package com.example.CWMS.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SortieRequestDTO {

    @NotBlank(message = "Le code lot est obligatoire")
    @Size(max = 50, message = "Code lot trop long")
    private String lotCode;

    // null = sortie totale, > 0 = sortie partielle
    @Min(value = 0, message = "La quantité ne peut pas être négative")
    private Double quantite;

    private boolean sortieComplete;

    // NE PAS mettre userId ici — il est lu depuis le JWT dans le service
    // deviceInfo vient du mobile, pas un champ sensible
    @Size(max = 200)
    private String deviceInfo;

    @Size(max = 500)
    private String notes;

    private String source; // "MOBILE" ou "WEB"
}