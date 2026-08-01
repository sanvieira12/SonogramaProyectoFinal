package com.sonograma.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleOAuthExchangeRequest(@NotBlank String code) {
}
