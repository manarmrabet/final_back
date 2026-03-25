// ─────────────────────────────────────────────────────────────────────────────
// FILE 2 : TransferResponseDTO.java  (Backend → Mobile/Web)
// ─────────────────────────────────────────────────────────────────────────────
package com.example.CWMS.transfer.dto;

import com.example.CWMS.transfer.model.StockTransfer;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponseDTO {

    private Long   id;
    private String erpItemCode;
    private String erpItemLabel;
    private String lotNumber;
    private String sourceLocation;
    private String destLocation;
    private Integer quantity;
    private String unit;
    private String status;
    private String transferType;
    private String operatorName;
    private String validatedByName;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime validatedAt;
    private String notes;
    private String errorMessage;

    /** Mapping depuis entité */
    public static TransferResponseDTO from(StockTransfer t) {
        return TransferResponseDTO.builder()
                .id(t.getId())
                .erpItemCode(t.getErpItemCode())
                .erpItemLabel(t.getErpItemLabel())
                .lotNumber(t.getLotNumber())
                .sourceLocation(t.getSourceLocation())
                .destLocation(t.getDestLocation())
                .quantity(t.getQuantity())
                .unit(t.getUnit())
                .status(t.getStatus())
                .transferType(t.getTransferType())
                .operatorName(t.getOperator() != null
                        ? t.getOperator().getFirstName() + " " + t.getOperator().getLastName()
                        : null)
                .validatedByName(t.getValidatedBy() != null
                        ? t.getValidatedBy().getFirstName() + " " + t.getValidatedBy().getLastName()
                        : null)
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .validatedAt(t.getValidatedAt())
                .notes(t.getNotes())
                .errorMessage(t.getErrorMessage())
                .build();
    }
}