package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscogsCoverDownloadDTO {
    private String imagenUrl;
    private String warning;
}
