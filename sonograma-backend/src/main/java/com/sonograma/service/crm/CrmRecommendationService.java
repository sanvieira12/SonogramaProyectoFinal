package com.sonograma.service.crm;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.entity.*;
import com.sonograma.enums.TipoInteresCrm;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.ClienteMapper;
import com.sonograma.repository.CrmInteresClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrmRecommendationService {

    private final DiscoRepository discoRepository;
    private final VentaRepository ventaRepository;
    private final CrmInteresClienteRepository interestRepository;
    private final CrmProfileService profileService;
    private final CrmInterestService interestService;
    private final CrmRecommendationProperties properties;

    public List<CrmDtos.Recomendacion> recommendations(Long customerId, Integer requestedLimit) {
        CrmProfileCalculator.CustomerProfile profile = profileService.profile(customerId);
        List<CrmInteresCliente> interests = interestService.activeEntities(customerId);
        int limit = limit(requestedLimit);

        return discoRepository.findAvailableForCrm().stream()
                .map(row -> new AvailableCandidate((Disco) row[0], ((Number) row[1]).longValue()))
                .filter(candidate -> candidate.disc() != null
                        && candidate.disc().getCatalogDeletedAt() == null
                        && candidate.disc().getEstado() == EstadoDisco.DISPONIBLE
                        && candidate.availableCopies() > 0)
                .filter(candidate -> !profile.purchasedDiscIds().contains(candidate.disc().getIdDisco()))
                .map(candidate -> scored(candidate.disc(), profile, interests, candidate.availableCopies()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Scored::score).reversed()
                        .thenComparing(scored -> CrmMetadataNormalizer.normalize(scored.disc().getArtista()))
                        .thenComparing(scored -> CrmMetadataNormalizer.normalize(scored.disc().getAlbum()))
                        .thenComparing(scored -> scored.disc().getIdDisco()))
                .limit(limit)
                .map(this::recommendationDto)
                .toList();
    }

    public List<CrmDtos.ClienteAfin> recommendedCustomers(Long discId, Integer requestedLimit) {
        Disco disc = discoRepository.findById(discId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", discId));
        int limit = limit(requestedLimit);
        Map<Long, List<Venta>> salesByCustomer = ventaRepository.findAllCompletedForCrm().stream()
                .collect(java.util.stream.Collectors.groupingBy(v -> v.getCliente().getIdCliente()));
        Map<Long, List<CrmInteresCliente>> interestsByCustomer = interestRepository.findAllActiveWithCustomer().stream()
                .collect(java.util.stream.Collectors.groupingBy(i -> i.getCliente().getIdCliente()));

        LinkedHashMap<Long, Cliente> customers = new LinkedHashMap<>();
        salesByCustomer.values().stream().flatMap(Collection::stream)
                .forEach(sale -> customers.putIfAbsent(sale.getCliente().getIdCliente(), sale.getCliente()));
        interestsByCustomer.values().stream().flatMap(Collection::stream)
                .forEach(interest -> customers.putIfAbsent(interest.getCliente().getIdCliente(), interest.getCliente()));

        return customers.values().stream()
                .map(customer -> {
                    CrmProfileCalculator.CustomerProfile profile = profileService.calculate(
                            customer, salesByCustomer.getOrDefault(customer.getIdCliente(), List.of()));
                    if (profile.purchasedDiscIds().contains(discId)) return null;
                    return scored(disc, profile,
                            interestsByCustomer.getOrDefault(customer.getIdCliente(), List.of()), 0);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Scored::score).reversed()
                        .thenComparing(scored -> scored.profile().cliente().getNombre(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(scored -> scored.profile().cliente().getIdCliente()))
                .limit(limit)
                .map(scored -> new CrmDtos.ClienteAfin(
                        ClienteMapper.toDTO(scored.profile().cliente()), scored.score(), affinity(scored.score()), scored.reasons()))
                .toList();
    }

    private Scored scored(Disco disc, CrmProfileCalculator.CustomerProfile profile,
                          List<CrmInteresCliente> interests, long availableCopies) {
        List<Contribution> contributions = new ArrayList<>();

        BigDecimal manualAffinity = manualAffinity(disc, interests);
        add(contributions, properties.getManualInterestWeight(), manualAffinity,
                manualReason(disc, interests, manualAffinity));

        BigDecimal artistAffinity = affinity(profile, CrmProfileCalculator.Taste::artists,
                List.of(CrmMetadataNormalizer.normalize(disc.getArtista())));
        add(contributions, properties.getArtistWeight(), artistAffinity,
                artistAffinity.signum() > 0 ? "Ya compró discos de " + disc.getArtista() : null);

        List<String> labelKeys = keys(CrmMetadataNormalizer.split(disc.getSelloDiscografico()));
        BigDecimal labelAffinity = affinity(profile, CrmProfileCalculator.Taste::labels, labelKeys);
        add(contributions, properties.getLabelWeight(), labelAffinity,
                labelAffinity.signum() > 0 ? disc.getSelloDiscografico() + " está entre sus sellos más comprados" : null);

        List<String> genreKeys = keys(CrmMetadataNormalizer.split(disc.getGenero()));
        BigDecimal genreAffinity = affinity(profile, CrmProfileCalculator.Taste::genres, genreKeys);
        add(contributions, properties.getGenreWeight(), genreAffinity,
                genreAffinity.signum() > 0 ? "Coincide con sus géneros habituales: " + disc.getGenero() : null);

        List<String> styleKeys = keys(CrmMetadataNormalizer.split(disc.getEstilo()));
        BigDecimal styleAffinity = affinity(profile, CrmProfileCalculator.Taste::styles, styleKeys);
        add(contributions, properties.getStyleWeight(), styleAffinity,
                styleAffinity.signum() > 0 ? "Coincide con estilos que compra: " + disc.getEstilo() : null);

        List<String> periodKeys = new ArrayList<>();
        if (disc.getAnio() != null) periodKeys.add(String.valueOf(disc.getAnio()));
        BigDecimal yearAffinity = affinity(profile, CrmProfileCalculator.Taste::years, periodKeys);
        String decade = CrmMetadataNormalizer.decade(disc.getAnio());
        BigDecimal decadeAffinity = affinity(profile, CrmProfileCalculator.Taste::decades,
                decade == null ? List.of() : List.of(CrmMetadataNormalizer.normalize(decade)));
        BigDecimal periodAffinity = yearAffinity.max(decadeAffinity);
        add(contributions, properties.getPeriodWeight(), periodAffinity,
                periodAffinity.signum() > 0 ? "El año " + disc.getAnio() + " encaja en sus períodos preferidos" : null);

        BigDecimal formatAffinity = affinity(profile, CrmProfileCalculator.Taste::formats,
                CrmMetadataNormalizer.format(disc.getFormato()).map(token -> List.of(token.key())).orElse(List.of()));
        add(contributions, properties.getFormatWeight(), formatAffinity,
                formatAffinity.signum() > 0 ? "Suele comprar el formato " + disc.getFormato() : null);

        BigDecimal conditionAffinity = affinity(profile, CrmProfileCalculator.Taste::conditions,
                disc.getCondicion() == null ? List.of() : List.of(CrmMetadataNormalizer.normalize(disc.getCondicion().name())));
        add(contributions, properties.getConditionWeight(), conditionAffinity,
                conditionAffinity.signum() > 0 ? "Coincide con su preferencia de condición" : null);

        PriceMatch priceMatch = priceMatch(disc.getPrecioVenta(), profile);
        add(contributions, properties.getPriceWeight(), priceMatch.affinity(), priceMatch.reason());

        boolean meaningful = manualAffinity.signum() > 0 || artistAffinity.signum() > 0 || labelAffinity.signum() > 0
                || genreAffinity.signum() > 0 || styleAffinity.signum() > 0 || periodAffinity.signum() > 0;
        if (!meaningful) return null;

        BigDecimal raw = contributions.stream().map(Contribution::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal score = raw.multiply(BigDecimal.valueOf(100))
                .divide(properties.maximumRawScore(), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
        List<String> reasons = contributions.stream()
                .filter(contribution -> contribution.reason() != null && !contribution.reason().isBlank())
                .sorted(Comparator.comparing(Contribution::points).reversed())
                .map(Contribution::reason)
                .distinct()
                .limit(6)
                .toList();
        return new Scored(disc, profile, availableCopies, score, reasons);
    }

    private BigDecimal affinity(CrmProfileCalculator.CustomerProfile profile,
                                Function<CrmProfileCalculator.Taste, Map<String, CrmProfileCalculator.DimensionStat>> dimension,
                                List<String> candidateKeys) {
        if (candidateKeys == null || candidateKeys.isEmpty()) return BigDecimal.ZERO;
        Map<String, CrmProfileCalculator.DimensionStat> historical = dimension.apply(profile.historical());
        Map<String, CrmProfileCalculator.DimensionStat> recent = dimension.apply(profile.recent());
        BigDecimal best = BigDecimal.ZERO;
        for (String key : candidateKeys) {
            if (key == null || key.isBlank()) continue;
            BigDecimal historicalShare = Optional.ofNullable(historical.get(key))
                    .map(CrmProfileCalculator.DimensionStat::share).orElse(BigDecimal.ZERO);
            BigDecimal recentShare = Optional.ofNullable(recent.get(key))
                    .map(CrmProfileCalculator.DimensionStat::share).orElse(BigDecimal.ZERO);
            BigDecimal combined = historicalShare.multiply(properties.getHistoricalWeight())
                    .add(recentShare.multiply(properties.getRecentWeight()));
            best = best.max(combined);
        }
        return best.min(BigDecimal.ONE);
    }

    private BigDecimal manualAffinity(Disco disc, List<CrmInteresCliente> interests) {
        BigDecimal combined = BigDecimal.ZERO;
        for (CrmInteresCliente interest : interests) {
            combined = combined.add(manualMatch(disc, interest));
        }
        return combined.min(BigDecimal.ONE);
    }

    private BigDecimal manualMatch(Disco disc, CrmInteresCliente interest) {
        String wanted = CrmMetadataNormalizer.normalize(interest.getTexto());
        if (wanted.isBlank()) return BigDecimal.ZERO;
        TipoInteresCrm type = interest.getTipo() == null ? TipoInteresCrm.LIBRE : interest.getTipo();
        if (type == TipoInteresCrm.FORMATO) {
            String wantedFormat = CrmMetadataNormalizer.format(interest.getTexto())
                    .map(CrmMetadataNormalizer.Token::key).orElse("");
            String candidateFormat = CrmMetadataNormalizer.format(disc.getFormato())
                    .map(CrmMetadataNormalizer.Token::key).orElse("");
            return !wantedFormat.isBlank() && wantedFormat.equals(candidateFormat)
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        String candidate = switch (type) {
            case ARTISTA -> disc.getArtista();
            case ALBUM -> disc.getAlbum();
            case GENERO -> disc.getGenero();
            case ESTILO -> disc.getEstilo();
            case SELLO -> disc.getSelloDiscografico();
            case FORMATO -> disc.getFormato();
            case CONDICION -> disc.getCondicion() != null ? disc.getCondicion().name() : null;
            case PAIS -> countryText(disc);
            case PERIODO -> disc.getAnio() == null ? null
                    : disc.getAnio() + " " + CrmMetadataNormalizer.decade(disc.getAnio());
            case LIBRE -> searchableText(disc);
        };
        if (type == TipoInteresCrm.PERIODO) {
            Set<Integer> mentioned = CrmMetadataNormalizer.mentionedYears(interest.getTexto());
            if (disc.getAnio() != null && (mentioned.contains(disc.getAnio())
                    || mentioned.contains(disc.getAnio() / 10 * 10)
                    || mentioned.contains(disc.getAnio() / 10 * 10 + 9))) return BigDecimal.ONE;
        }
        String normalizedCandidate = CrmMetadataNormalizer.normalize(candidate);
        if (normalizedCandidate.isBlank()) return BigDecimal.ZERO;
        if (type != TipoInteresCrm.LIBRE) {
            return normalizedCandidate.contains(wanted) || wanted.contains(normalizedCandidate)
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        Set<String> terms = CrmMetadataNormalizer.meaningfulTerms(interest.getTexto());
        if (terms.isEmpty()) return BigDecimal.ZERO;
        long matched = terms.stream().filter(normalizedCandidate::contains).count();
        if (matched == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(matched).divide(BigDecimal.valueOf(terms.size()), 8, RoundingMode.HALF_UP);
    }

    private String manualReason(Disco disc, List<CrmInteresCliente> interests, BigDecimal affinity) {
        if (affinity.signum() == 0) return null;
        return interests.stream()
                .filter(interest -> manualMatch(disc, interest).signum() > 0)
                .max(Comparator.comparing(interest -> manualMatch(disc, interest)))
                .map(interest -> "Coincide con el interés explícito: “" + interest.getTexto() + "”")
                .orElse(null);
    }

    private PriceMatch priceMatch(BigDecimal price, CrmProfileCalculator.CustomerProfile profile) {
        if (price == null || price.signum() < 0 || profile.metrics().unitPrices().isEmpty()) {
            return new PriceMatch(BigDecimal.ZERO, null);
        }
        PriceStats historical = stats(profile.metrics().unitPrices());
        List<BigDecimal> recentPrices = new ArrayList<>();
        LocalDateTime recentStart = LocalDateTime.now().minusMonths(12);
        profile.lines().stream().filter(line -> !line.fechaCompra().isBefore(recentStart))
                .filter(line -> line.precioUnitarioPagado() != null)
                .forEach(line -> {
                    for (int i = 0; i < line.cantidad(); i++) recentPrices.add(line.precioUnitarioPagado());
                });
        BigDecimal historicalAffinity = priceAffinity(price, historical);
        BigDecimal recentAffinity = recentPrices.isEmpty()
                ? BigDecimal.ZERO : priceAffinity(price, stats(recentPrices));
        BigDecimal affinity = historicalAffinity.multiply(properties.getHistoricalWeight())
                .add(recentAffinity.multiply(properties.getRecentWeight()));
        String reason;
        if (price.compareTo(historical.q1()) >= 0 && price.compareTo(historical.q3()) <= 0) {
            reason = "El precio está dentro de su rango habitual";
        } else if (price.compareTo(historical.q1()) < 0) {
            reason = "El precio está por debajo de su rango habitual";
        } else if (price.compareTo(historical.maximum()) <= 0) {
            reason = "El precio está algo por encima de su rango habitual";
        } else {
            reason = "El precio supera su máximo histórico, pero no bloquea la recomendación";
        }
        return new PriceMatch(affinity.min(BigDecimal.ONE), reason);
    }

    private BigDecimal priceAffinity(BigDecimal price, PriceStats stats) {
        if (price.compareTo(stats.q1()) >= 0 && price.compareTo(stats.q3()) <= 0) return BigDecimal.ONE;
        if (price.compareTo(stats.q1()) < 0) return new BigDecimal("0.75");
        if (price.compareTo(stats.maximum()) <= 0) return new BigDecimal("0.60");
        if (price.signum() == 0) return BigDecimal.ONE;
        return new BigDecimal("0.60").multiply(stats.maximum()).divide(price, 8, RoundingMode.HALF_UP)
                .max(new BigDecimal("0.15"));
    }

    private PriceStats stats(List<BigDecimal> prices) {
        List<BigDecimal> sorted = prices.stream().sorted().toList();
        return new PriceStats(percentile(sorted, .25), percentile(sorted, .75), sorted.get(sorted.size() - 1));
    }

    private BigDecimal percentile(List<BigDecimal> sorted, double p) {
        if (sorted.size() == 1) return sorted.get(0);
        double position = (sorted.size() - 1) * p;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted.get(lower);
        return sorted.get(lower).add(sorted.get(upper).subtract(sorted.get(lower))
                .multiply(BigDecimal.valueOf(position - lower)));
    }

    private void add(List<Contribution> contributions, BigDecimal weight, BigDecimal affinity, String reason) {
        if (affinity == null || affinity.signum() <= 0) return;
        contributions.add(new Contribution(weight.multiply(affinity), reason));
    }

    private List<String> keys(List<CrmMetadataNormalizer.Token> tokens) {
        return tokens.stream().map(CrmMetadataNormalizer.Token::key).toList();
    }

    private String searchableText(Disco disc) {
        return String.join(" ", nonNull(disc.getArtista()), nonNull(disc.getAlbum()), nonNull(disc.getGenero()),
                nonNull(disc.getEstilo()), nonNull(disc.getSelloDiscografico()), nonNull(disc.getFormato()),
                disc.getCondicion() != null ? disc.getCondicion().name() : "", countryText(disc),
                disc.getAnio() != null ? disc.getAnio() + " " + CrmMetadataNormalizer.decade(disc.getAnio()) : "");
    }

    private String countryText(Disco disc) {
        String country = nonNull(disc.getPais());
        String normalized = CrmMetadataNormalizer.normalize(country);
        if (Set.of("alemania", "aleman", "alemana", "german").contains(normalized)) country += " germany";
        if (Set.of("reino unido", "inglaterra", "british").contains(normalized)) country += " uk";
        if (Set.of("estados unidos", "united states").contains(normalized)) country += " usa";
        return country + " " + nonNull(disc.getProcedencia());
    }

    private String nonNull(String value) { return value == null ? "" : value; }

    private int limit(Integer requested) {
        int value = requested == null ? properties.getDefaultLimit() : requested;
        return Math.max(1, Math.min(properties.getMaximumLimit(), value));
    }

    private String affinity(BigDecimal score) {
        if (score.compareTo(properties.getHighAffinityThreshold()) >= 0) return "ALTA";
        if (score.compareTo(properties.getMediumAffinityThreshold()) >= 0) return "MEDIA";
        return "BAJA";
    }

    private CrmDtos.Recomendacion recommendationDto(Scored scored) {
        Disco disc = scored.disc();
        return new CrmDtos.Recomendacion(
                disc.getIdDisco(), disc.getArtista(), disc.getAlbum(), disc.getSelloDiscografico(), disc.getAnio(),
                disc.getGenero(), disc.getEstilo(), disc.getFormato(),
                disc.getCondicion() != null ? disc.getCondicion().name() : null,
                disc.getImagenUrl(), disc.getPrecioVenta(), scored.availableCopies(), scored.score(),
                affinity(scored.score()), scored.reasons()
        );
    }

    private record AvailableCandidate(Disco disc, long availableCopies) {}
    private record Contribution(BigDecimal points, String reason) {}
    private record PriceMatch(BigDecimal affinity, String reason) {}
    private record PriceStats(BigDecimal q1, BigDecimal q3, BigDecimal maximum) {}
    private record Scored(Disco disc, CrmProfileCalculator.CustomerProfile profile, long availableCopies,
                          BigDecimal score, List<String> reasons) {}
}
