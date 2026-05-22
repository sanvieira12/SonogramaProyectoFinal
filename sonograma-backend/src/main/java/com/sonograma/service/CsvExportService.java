package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a RFC-4180 CSV with UTF-8 BOM from a list of InvoiceItems and their vinylfuture URLs.
 *
 * Columns: artista, album, url_vinylfuture, estado_match
 * estado_match: ENCONTRADO | NO_ENCONTRADO
 */
@Service
public class CsvExportService {

    private static final String BOM = "\uFEFF";
    private static final String HEADER = "artista,album,url_vinylfuture,estado_match\n";

    /**
     * @param results Map of InvoiceItem -> Optional URL from vinylfuture
     */
    public String buildCsv(Map<InvoiceItem, Optional<String>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(BOM);
        sb.append(HEADER);

        for (Map.Entry<InvoiceItem, Optional<String>> entry : results.entrySet()) {
            InvoiceItem item = entry.getKey();
            Optional<String> url = entry.getValue();

            sb.append(escapeCsvField(item.artista())).append(',');
            sb.append(escapeCsvField(item.album())).append(',');
            sb.append(escapeCsvField(url.orElse(""))).append(',');
            sb.append(url.isPresent() ? "ENCONTRADO" : "NO_ENCONTRADO");
            sb.append('\n');
        }

        return sb.toString();
    }

    /** RFC 4180: if field contains comma, double-quote, or newline → wrap in double quotes and escape inner quotes. */
    private String escapeCsvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
