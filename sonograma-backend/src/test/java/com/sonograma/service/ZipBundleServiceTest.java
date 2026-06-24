package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class ZipBundleServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void zipIncludesPersistedMediaFolderWithCoverAndMp3() throws Exception {
        Path folder = tempDir.resolve("ALT025 - Betonkust - Tropicana Tracks Two");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("cover.jpg"), "cover");
        Files.writeString(folder.resolve("A1 - Dont Think Ill Be Here Too Long.mp3"), "mp3");

        VinylFutureAssetService assetService = new VinylFutureAssetService(tempDir.toString());
        ZipBundleService zipBundleService = new ZipBundleService(assetService);
        InvoiceItem item = new InvoiceItem(
            "ALT025", "Betonkust", "Tropicana Tracks Two", "12",
            BigDecimal.ONE, 1, BigDecimal.ONE);
        VinylPageData page = new VinylPageData(
            "https://www.vinylfuture.com/Betonkust_Tropicana_Tracks_Two_ALT025_Vinyl__1225612",
            "Betonkust", "Tropicana Tracks Two", "ALT025", null, null, null,
            null, "12", null, null, BigDecimal.ONE,
            "/api/importar/vinylfuture/media/ALT025%20-%20Betonkust%20-%20Tropicana%20Tracks%20Two/cover.jpg",
            null,
            List.of(new TrackInfo(
                "A1",
                "Dont Think Ill Be Here Too Long",
                "/api/importar/vinylfuture/media/ALT025%20-%20Betonkust%20-%20Tropicana%20Tracks%20Two/A1%20-%20Dont%20Think%20Ill%20Be%20Here%20Too%20Long.mp3",
                null))
        );
        Map<InvoiceItem, Optional<VinylPageData>> pageData = new LinkedHashMap<>();
        pageData.put(item, Optional.of(page));

        Path zip = zipBundleService.buildZip("codigo,artista\nALT025,Betonkust\n", pageData);

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            List<String> names = zipFile.stream().map(entry -> entry.getName()).toList();
            String root = names.stream()
                .filter(name -> name.startsWith("vinylfuture-export-") && name.endsWith("/import.csv"))
                .findFirst()
                .orElseThrow()
                .replace("/import.csv", "");
            assertThat(names).contains(
                root + "/import.csv",
                root + "/data/import.csv",
                root + "/media/ALT025 - Betonkust - Tropicana Tracks Two/cover.jpg",
                root + "/media/ALT025 - Betonkust - Tropicana Tracks Two/A1 - Dont Think Ill Be Here Too Long.mp3"
            );
        } finally {
            Files.deleteIfExists(zip);
        }
    }
}
