package com.sonograma.service.importacion;

import com.sonograma.enums.DiscogsImportRowStatus;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DiscogsExcelParserTest {

    private final DiscogsExcelParser parser = new DiscogsExcelParser(new DiscogsLinkParser());

    @Test
    void readsVisibleAndEmbeddedReleaseAndMasterLinksBeforeScraping() throws Exception {
        DiscogsExcelParser.ParsedSheet sheet = parser.parse(workbookFixture());

        assertThat(sheet.rows()).hasSize(5);
        assertThat(sheet.ignoredBlankRows()).isEqualTo(1);
        assertThat(sheet.rows()).filteredOn(row -> row.discogsId() != null).hasSize(4);

        var visibleRelease = sheet.rows().get(0);
        assertThat(visibleRelease.urlSource()).isEqualTo("visible");
        assertThat(visibleRelease.discogsType()).isEqualTo("release");
        assertThat(visibleRelease.discogsId()).isEqualTo(101L);

        var hyperlinkRelease = sheet.rows().get(1);
        assertThat(hyperlinkRelease.visibleCellValue()).isEqualTo("Ver ficha");
        assertThat(hyperlinkRelease.hyperlinkUrl()).contains("/es/release/202");
        assertThat(hyperlinkRelease.urlSource()).isEqualTo("hyperlink");
        assertThat(hyperlinkRelease.discogsId()).isEqualTo(202L);

        var visibleMaster = sheet.rows().get(2);
        assertThat(visibleMaster.discogsType()).isEqualTo("master");
        assertThat(visibleMaster.discogsId()).isEqualTo(303L);

        var hyperlinkMaster = sheet.rows().get(3);
        assertThat(hyperlinkMaster.discogsType()).isEqualTo("master");
        assertThat(hyperlinkMaster.discogsId()).isEqualTo(404L);

        assertThat(sheet.rows().get(4).status()).isEqualTo(DiscogsImportRowStatus.NEEDS_MANUAL_MATCH);
    }

    @Test
    void ignoresFormattedBlankRowsAndReadsTemplateColumns() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Discogs");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("link de discogs");
            header.createCell(1).setCellValue("condicion");
            header.createCell(2).setCellValue("precio");
            header.createCell(3).setCellValue("GENERO");
            header.createCell(4).setCellValue("estado");
            header.createCell(5).setCellValue("CODIGO");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("DJ Fex – Acid Forever – Vinyl (12\", 33 ⅓ RPM), 2007 [r960977] | Discogs");
            row.createCell(1).setCellValue("VG++");
            row.createCell(2).setCellValue("$800");
            row.createCell(3).setCellValue("Techno");
            row.createCell(5).setCellValue("LOTE-A");

            var sold = sheet.createRow(2);
            sold.createCell(0).setCellValue("https://www.discogs.com/es/sell/release/20923924");
            sold.createCell(2).setCellValue("SIN PRECIO");
            sold.createCell(4).setCellValue("vendido");

            for (int i = 3; i < 1000; i++) {
                sheet.createRow(i).createCell(5).setCellStyle(workbook.createCellStyle());
            }

            workbook.write(output);
            var file = new MockMultipartFile("file", "sample.xlsx", "application/xlsx", output.toByteArray());

            var parsed = parser.parse(file);

            assertThat(parsed.rows()).hasSize(2);
            assertThat(parsed.ignoredBlankRows()).isEqualTo(997);
            assertThat(parsed.physicalExcelLastRow()).isEqualTo(1000);

            var copiedText = parsed.rows().get(0);
            assertThat(copiedText.discogsType()).isEqualTo("release");
            assertThat(copiedText.discogsId()).isEqualTo(960977L);
            assertThat(copiedText.artist()).isEqualTo("DJ Fex");
            assertThat(copiedText.title()).isEqualTo("Acid Forever");
            assertThat(copiedText.urlSource()).isEqualTo("visible_r_id");
            assertThat(copiedText.manualCondition()).isEqualTo("VG++");
            assertThat(copiedText.manualPriceUyu()).isEqualByComparingTo(new BigDecimal("800"));
            assertThat(copiedText.manualGenre()).isEqualTo("Techno");
            assertThat(copiedText.sourceStatus()).isEqualTo("DISPONIBLE");
            assertThat(copiedText.internalCode()).isEqualTo("LOTE-A");

            var soldRow = parsed.rows().get(1);
            assertThat(soldRow.discogsType()).isEqualTo("release");
            assertThat(soldRow.discogsId()).isEqualTo(20923924L);
            assertThat(soldRow.status()).isEqualTo(DiscogsImportRowStatus.SOLD);
            assertThat(soldRow.manualPriceUyu()).isNull();
            assertThat(soldRow.errorMessage()).contains("SIN PRECIO");
        }
    }

    @Test
    void marksRepeatedDiscogsIdsAsDuplicates() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Discogs");
            sheet.createRow(0).createCell(0).setCellValue("Discogs URL");
            sheet.createRow(1).createCell(0).setCellValue("https://discogs.com/release/101-one");
            sheet.createRow(2).createCell(0).setCellValue("https://www.discogs.com/release/101-two?x=1");
            workbook.write(output);
            var file = new MockMultipartFile(
                    "file", "duplicates.xlsx", "application/xlsx", output.toByteArray()
            );

            var rows = parser.parse(file).rows();

            assertThat(rows.get(0).status()).isEqualTo(DiscogsImportRowStatus.PARSED);
            assertThat(rows.get(1).status()).isEqualTo(DiscogsImportRowStatus.IGNORED);
            assertThat(rows.get(1).errorMessage()).contains("duplicado");
        }
    }

    private MockMultipartFile workbookFixture() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Discogs");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Artista");
            header.createCell(1).setCellValue("Álbum");
            header.createCell(2).setCellValue("Link");

            var visibleRelease = sheet.createRow(1);
            visibleRelease.createCell(2).setCellValue("www.discogs.com/release/101-release-name");

            var embeddedRelease = sheet.createRow(2);
            var embeddedReleaseCell = embeddedRelease.createCell(2);
            embeddedReleaseCell.setCellValue("Ver ficha");
            var releaseLink = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            releaseLink.setAddress("https://www.discogs.com/es/release/202-title");
            embeddedReleaseCell.setHyperlink(releaseLink);

            var visibleMaster = sheet.createRow(3);
            visibleMaster.createCell(2).setCellValue("discogs.com/master/303");

            var embeddedMaster = sheet.createRow(4);
            var embeddedMasterCell = embeddedMaster.createCell(2);
            embeddedMasterCell.setCellValue("Abrir master");
            var masterLink = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            masterLink.setAddress("http://discogs.com/fr/master/404-master-title");
            embeddedMasterCell.setHyperlink(masterLink);

            var manual = sheet.createRow(5);
            manual.createCell(0).setCellValue("Artista manual");
            manual.createCell(1).setCellValue("Álbum manual");

            sheet.createRow(6);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "discogs.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }
}
