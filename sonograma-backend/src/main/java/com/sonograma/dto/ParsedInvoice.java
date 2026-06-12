package com.sonograma.dto;

import java.math.BigDecimal;
import java.util.List;

public record ParsedInvoice(
    List<InvoiceItem> items,
    List<String> productLinks,
    BigDecimal total
) {}
