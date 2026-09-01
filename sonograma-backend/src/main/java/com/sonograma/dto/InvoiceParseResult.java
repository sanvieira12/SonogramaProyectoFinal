package com.sonograma.dto;

import java.util.List;

public record InvoiceParseResult(
    ParsedInvoice invoice,
    List<InvoiceSourceRowDTO> sourceRows,
    List<String> warnings,
    List<String> errors
) {}
