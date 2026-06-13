package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "disco_qr_copy",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_disco_qr_copy_code", columnNames = "codigo_qr"),
        @UniqueConstraint(name = "uk_disco_qr_copy_number", columnNames = {"id_disco", "copy_number"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoQrCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_disco", nullable = false)
    private Long idDisco;

    @Column(name = "copy_number", nullable = false)
    private Integer copyNumber;

    @Column(name = "codigo_qr", nullable = false, unique = true)
    private String codigoQr;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
