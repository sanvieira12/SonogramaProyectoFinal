package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscogsZipStatusDTO {
    private Long jobId;
    private String zipStatus;
    private int zipTotalCovers;
    private int zipProcessedCovers;
    private int zipAddedCovers;
    private int zipFailedCovers;
    private int zipProgressPercentage;
    private String zipCurrentRelease;
    private boolean zipReady;
    private String zipError;
}
