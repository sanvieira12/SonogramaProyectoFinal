package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscogsCoverZipRow {
    private int sourceExcelRowNumber;
    private String discogsUrl;
    private String sourceType;
    private Long sourceDiscogsId;
    private Long resolvedReleaseId;
    private String artist;
    private String title;
    private BigDecimal priceUyu;
    private String priceRaw;
    private String condition;
    private String sourceStatus;
    private String metadataStatus;
    private String metadataErrorCode;
    private String coverStatus;
    private String coverErrorCode;
    private String youtubeStatus;
    private String catalogImportStatus;
    private String imageUrl;
    private String coverLocalPath;
    private Long catalogDiscoId;
    private String codigoQr;
    private String warningMessage;
    private String errorMessage;
}
