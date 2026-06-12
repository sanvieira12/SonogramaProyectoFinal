package com.sonograma.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscogsImportJobDTO {
    private Long id;
    private String nombreArchivo;
    private String nombreHoja;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int totalRowsRead;
    private int validReleaseUrls;
    private int validMasterUrls;
    private int embeddedHyperlinkRows;
    private int needsManualMatch;
    private int ignored;
    private int failed;
    private int rateLimited;
    private int imported;
    private int coversDownloaded;
    private int pending;
    private List<DiscogsImportRowDTO> rows;
}
