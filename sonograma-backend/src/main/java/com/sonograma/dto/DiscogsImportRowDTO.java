package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

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
    private String rawCondition;
    private String manualCondition;
    private String rawPrice;
    private BigDecimal manualPriceUyu;
    private String manualGenre;
    private String observation;
    private String sourceStatus;
    private String internalCode;
    private Integer year;
    private String genre;
    private String label;
    private String catalogNumber;
    private String country;
    private String style;
    private String format;
    private String tracklist;
    private int youtubeLinksFound;
    private String imageUrl;
    private String status;
    private String errorMessage;
    private Integer retryCount;
    private Long importedCatalogProductId;
}
