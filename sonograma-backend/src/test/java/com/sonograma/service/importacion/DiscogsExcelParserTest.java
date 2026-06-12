package com.sonograma.service.importacion;

import com.sonograma.enums.DiscogsImportRowStatus;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DiscogsExcelParserTest {

    private final DiscogsExcelParser parser = new DiscogsExcelParser(new DiscogsLinkParser());

    @Test
    void readsVisibleAndEmbeddedReleaseAndMasterLinksBeforeScraping() throws Exception {
        DiscogsExcelParser.ParsedSheet sheet = parser.parse(workbookFixture());

        assertThat(sheet.rows()).hasSize(6);
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
        assertThat(sheet.rows().get(5).status()).isEqualTo(DiscogsImportRowStatus.IGNORED);
        assertThat(sheet.rows().get(5).errorMessage())
                .isEqualTo("Fila sin artista ni álbum y sin link Discogs");
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
