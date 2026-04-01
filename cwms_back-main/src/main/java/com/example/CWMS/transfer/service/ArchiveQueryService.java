package com.example.CWMS.transfer.service;

import com.example.CWMS.transfer.dto.TransferResponseDTO;
import com.example.CWMS.transfer.model.StockTransferArchive;
import com.example.CWMS.transfer.repository.StockTransferArchiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveQueryService {

    private final StockTransferArchiveRepository archiveRepo;

    @Transactional(readOnly = true)
    public Page<TransferResponseDTO> searchArchive(
            String status,
            String itemCode,
            String location,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        String s = blank(status) ? null : status.trim();
        String i = blank(itemCode) ? null : itemCode.trim();
        String l = blank(location) ? null : location.trim();

        log.debug("searchArchive() → status={} itemCode={} location={} from={} to={}", s, i, l, from, to);

        Page<TransferResponseDTO> result = archiveRepo
                .searchArchive(s, i, l, from, to, pageable)
                .map(this::fromArchive);

        log.debug("searchArchive() → {} résultats (total={})",
                result.getNumberOfElements(), result.getTotalElements());

        return result;
    }

    private TransferResponseDTO fromArchive(StockTransferArchive a) {
        return TransferResponseDTO.builder()
                .id(a.getId())
                .erpItemCode(a.getErpItemCode())
                .erpItemLabel(a.getErpItemLabel())
                .lotNumber(a.getLotNumber())
                .sourceLocation(a.getSourceLocation())
                .destLocation(a.getDestLocation())
                .sourceWarehouse(a.getSourceWarehouse())
                .destWarehouse(a.getDestWarehouse())
                .quantity(a.getQuantity())
                .unit(a.getUnit())
                .status(a.getStatus())
                .transferType(a.getTransferType())
                .notes(a.getNotes())
                .errorMessage(a.getErrorMessage())
                .operatorName(a.getOperator() != null
                        ? a.getOperator().getFirstName() + " " + a.getOperator().getLastName()
                        : null)
                .createdAt(a.getCreatedAt())
                .completedAt(a.getCompletedAt())
                .validatedAt(a.getValidatedAt())
                .build();
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}