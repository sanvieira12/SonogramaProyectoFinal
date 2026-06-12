package com.sonograma.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedInvoice(
    List<InvoiceItem> items,
    List<String> productLinks,
    BigDecimal total,
    // Summary row (Quantity Postage Fees Net … Total)
    Integer cantidadTotalPdf,
    BigDecimal franqueo,
    BigDecimal tarifas,
    BigDecimal neto,
    // Header fields (best-effort from PDF text)
    String numeroFactura,
    LocalDate fechaFactura,
    String proveedor,
    String envio,
    String pago,
    String unidadPeso,
    String moneda,
    BigDecimal pesoTotalKg,
    String terminosVenta,
    String codigoArancel,
    String eoriNo,
    BigDecimal iva,
    String rawExtractText
) {}
