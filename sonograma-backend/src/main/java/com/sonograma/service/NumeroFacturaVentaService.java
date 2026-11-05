package com.sonograma.service;

import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NumeroFacturaVentaService {

    private static final int ADVISORY_LOCK_NAMESPACE = 0x534F4E4F; // "SONO"

    private final VentaRepository ventaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public String generar(int anio) {
        bloquearNumeracionEnPostgres(anio);

        Pattern formato = Pattern.compile("^F-" + anio + "-(\\d+)$");
        long mayorNumero = ventaRepository.findNumerosFacturaByAnio(anio).stream()
                .map(formato::matcher)
                .filter(Matcher::matches)
                .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
                .max()
                .orElse(0L);

        return String.format("F-%d-%03d", anio, mayorNumero + 1);
    }

    /**
     * Serializa la asignacion por anio hasta que finaliza la transaccion de la venta.
     * H2 se usa solo para desarrollo/tests y no ofrece advisory locks de PostgreSQL.
     */
    private void bloquearNumeracionEnPostgres(int anio) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                return null;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, ADVISORY_LOCK_NAMESPACE);
                statement.setInt(2, anio);
                statement.execute();
            }
            return null;
        });
    }
}
