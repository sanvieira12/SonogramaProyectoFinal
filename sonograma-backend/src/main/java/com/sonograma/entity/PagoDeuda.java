package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pago_deuda")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoDeuda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago_deuda")
    private Long idPagoDeuda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_deuda", nullable = false)
    private Deuda deuda;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "notas")
    private String notas;

    /** Receipt number belonging to this payment, not to the debt movement. */
    @Column(name = "numero_recibo")
    private String numeroRecibo;

    /** Client-generated key used to make payment retries idempotent. */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Financial records are retained and marked as reversed instead of deleted. */
    @Column(name = "anulado", nullable = false)
    @Builder.Default
    private Boolean anulado = false;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    /** Username from the authenticated session that performed the reversal. */
    @Column(name = "anulado_por", length = 150)
    private String anuladoPor;
}
