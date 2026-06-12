package com.sonograma.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscogsImportRowDTO {
    private Long id;
    private Integer sourceExcelRowNumber;
    private String visibleCellValue;
    private String hyperlinkUrl;
    private String normalizedDiscogsUrl;
    private String urlSource;
    private String discogsType;
    private Long discogsId;
    private Long masterId;
    private Long resolvedReleaseId;
    private String artist;
    private String title;
    private Integer year;
    private String genre;
    private String label;
    private String catalogNumber;
    private String imageUrl;
    private String status;
    private String errorMessage;
    private Integer retryCount;
    private Long importedCatalogProductId;
}
