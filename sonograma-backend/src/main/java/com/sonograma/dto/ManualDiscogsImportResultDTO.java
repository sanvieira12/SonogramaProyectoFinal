package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualDiscogsImportResultDTO {
    private String operationId;
    private Long productId;
    private String resultType;
    private Integer copiesAdded;
    private Integer availableCopies;
    private boolean alreadyProcessed;
    private String warning;
    private Long batchId;
    private List<Long> copyIds;
    private String sourceCustomerCode;
}
