package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
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
    void zipIncludesAlbumFoldersWithImagenesAndAudios() throws Exception {
        Path folder = tempDir.resolve("ALT025 - Betonkust - Tropicana Tracks Two");
        Files.createDirectories(folder);
        writePng(folder.resolve("cover.png"));
        Files.write(folder.resolve("A1 - Dont Think Ill Be Here Too Long.mp3"), new byte[] {'I', 'D', '3', 4, 0, 0});

        VinylFutureAssetService assetService = new VinylFutureAssetService(tempDir.toString());
        ZipBundleService zipBundleService = new ZipBundleService(assetService);
        InvoiceItem item = new InvoiceItem(
            "ALT025", "Betonkust", "Tropicana Tracks Two", "12",
            BigDecimal.ONE, 1, BigDecimal.ONE);
        VinylPageData page = new VinylPageData(
            "https://www.vinylfuture.com/Betonkust_Tropicana_Tracks_Two_ALT025_Vinyl__1225612",
            "Betonkust", "Tropicana Tracks Two", "ALT025", null, null, null,
            null, "12", null, null, BigDecimal.ONE,
            "/api/importar/vinylfuture/media/ALT025%20-%20Betonkust%20-%20Tropicana%20Tracks%20Two/cover.png",
            null,
            List.of(new TrackInfo(
                "A1",
                "Dont Think Ill Be Here Too Long",
                "/api/importar/vinylfuture/media/ALT025%20-%20Betonkust%20-%20Tropicana%20Tracks%20Two/A1%20-%20Dont%20Think%20Ill%20Be%20Here%20Too%20Long.mp3",
                null))
        );
        Map<InvoiceItem, Optional<VinylPageData>> pageData = new LinkedHashMap<>();
        pageData.put(item, Optional.of(page));

        Path zip = zipBundleService.buildZip("codigo,artista\nALT025,Betonkust\n", pageData, "VinylFuture_Invoice_INV-42");

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            List<String> names = zipFile.stream().map(entry -> entry.getName()).toList();
            assertThat(names).contains(
                "VinylFuture_Invoice_INV-42/import.csv",
                "VinylFuture_Invoice_INV-42/ALT025 - Tropicana Tracks Two/Imagenes/ALT025 - Tropicana Tracks Two - Cover.jpg",
                "VinylFuture_Invoice_INV-42/ALT025 - Tropicana Tracks Two/Audios/ALT025 - A1 - Dont Think Ill Be Here Too Long.mp3"
            );
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    @Test
    void zipAddsMissingMediaReportWithoutFailingWholeBundle() throws Exception {
        Path folder = tempDir.resolve("CAT-9 - Artist - Album");
        Files.createDirectories(folder);
        writePng(folder.resolve("cover.png"));

        VinylFutureAssetService assetService = new VinylFutureAssetService(tempDir.toString());
        ZipBundleService zipBundleService = new ZipBundleService(assetService);
        InvoiceItem item = new InvoiceItem("CAT-9", "Artist", "Album", "12", BigDecimal.ONE, 1, BigDecimal.ONE);
        VinylPageData page = new VinylPageData(
            "https://supplier.example/release",
            "Artist", "Album", "CAT-9", null, null, null, null, "12", null, null, BigDecimal.ONE,
            "/api/importar/vinylfuture/media/CAT-9%20-%20Artist%20-%20Album/cover.png",
            null,
            List.of(new TrackInfo("A1", "Track Missing", null, null))
        );
        Map<InvoiceItem, Optional<VinylPageData>> pageData = new LinkedHashMap<>();
        pageData.put(item, Optional.of(page));

        Path zip = zipBundleService.buildZip("codigo\nCAT-9\n", pageData, "VinylFuture_Invoice_2026-07-07");

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            String missingPath = "VinylFuture_Invoice_2026-07-07/CAT-9 - Album/missing_media.txt";
            assertThat(zipFile.getEntry(missingPath)).isNotNull();
            String missing = new String(zipFile.getInputStream(zipFile.getEntry(missingPath)).readAllBytes());
            assertThat(missing).contains("Audio: falta preview");
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    @Test
    void zipCreatesAlbumFolderEvenWhenMetadataIsMissing() throws Exception {
        VinylFutureAssetService assetService = new VinylFutureAssetService(tempDir.toString());
        ZipBundleService zipBundleService = new ZipBundleService(assetService);
        Map<InvoiceItem, Optional<VinylPageData>> pageData = new LinkedHashMap<>();
        pageData.put(new InvoiceItem(
            "NF-001", "Artista", "Album sin metadata", "12",
            new BigDecimal("10.00"), 1, new BigDecimal("10.00")), Optional.empty());

        Path zip = zipBundleService.buildZip("codigo,artista\nNF-001,Artista\n", pageData, "VinylFuture_Invoice_2026-07-07");

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            List<String> names = zipFile.stream().map(entry -> entry.getName()).toList();
            assertThat(names).contains(
                "VinylFuture_Invoice_2026-07-07/import.csv",
                "VinylFuture_Invoice_2026-07-07/NF-001 - Album sin metadata/missing_media.txt"
            );
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    private void writePng(Path target) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 4, 4);
        graphics.dispose();
        ImageIO.write(image, "png", target.toFile());
    }
}
