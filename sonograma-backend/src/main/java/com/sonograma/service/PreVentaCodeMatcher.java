package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.PreVenta;
import com.sonograma.repository.PreVentaRepository;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreVentaCodeMatcher {
    private final PreVentaRepository preVentaRepository;
    private final DiscoRepository discoRepository;

    public static String normalize(String code) {
        if (code == null) return null;
        String normalized = code.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    @Transactional
    public int linkPendingPreSales(Disco disco) {
        if (disco == null || disco.getIdDisco() == null) return 0;
        String code = normalize(disco.getCodigoInterno());
        if (code == null) return 0;
        List<Disco> catalogMatches = discoRepository.findAll().stream()
            .filter(candidate -> code.equals(normalize(candidate.getCodigoInterno())))
            .toList();
        if (catalogMatches.size() > 1) {
            log.warn("No se vincularon pre-ventas: código '{}' es ambiguo entre discos {}", code,
                catalogMatches.stream().map(Disco::getIdDisco).toList());
            return 0;
        }
        List<PreVenta> matches = preVentaRepository
            .findByCodigoDiscoNormalizadoAndDiscoIsNullAndEstadoNot(code, "PAGADA");
        matches.forEach(p -> p.setDisco(disco));
        if (!matches.isEmpty()) {
            preVentaRepository.saveAll(matches);
            log.info("Vinculadas {} pre-ventas pendientes al disco {} por código normalizado '{}'",
                matches.size(), disco.getIdDisco(), code);
        }
        return matches.size();
    }
}
