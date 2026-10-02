package com.sonograma.repository;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class RetainedCopyLifecycleMigrationTest {

    @Test
    void migration051IsAdditiveReplaySafeAndLeavesLegacyLifecycleDataNull() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "..", "docs", "migraciones",
                "051_retained_physical_copy_lifecycle.sql").normalize();
        assertThat(migration).exists();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:retained-copy-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE disco_qr_copy (id BIGINT PRIMARY KEY, estado VARCHAR(20) NOT NULL)");
            statement.execute("INSERT INTO disco_qr_copy (id, estado) VALUES (1, 'DISPONIBLE'), (2, 'VENDIDO')");

            run(connection, migration);
            run(connection, migration);

            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "DISCO_QR_COPY", null)) {
                int lifecycleColumns = 0;
                while (columns.next()) {
                    String name = columns.getString("COLUMN_NAME");
                    if (name.startsWith("DISPOSITION_") || name.equals("DISPOSED_AT")
                            || name.equals("DISPOSED_BY") || name.equals("UPDATED_AT")) {
                        lifecycleColumns++;
                    }
                }
                assertThat(lifecycleColumns).isEqualTo(5);
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT estado, disposition_reason, disposition_note, disposed_at, disposed_by, updated_at "
                            + "FROM disco_qr_copy ORDER BY id")) {
                int count = 0;
                while (rows.next()) {
                    count++;
                    assertThat(rows.getString("estado")).isIn("DISPONIBLE", "VENDIDO");
                    assertThat(rows.getObject("disposition_reason")).isNull();
                    assertThat(rows.getObject("disposition_note")).isNull();
                    assertThat(rows.getObject("disposed_at")).isNull();
                    assertThat(rows.getObject("disposed_by")).isNull();
                    assertThat(rows.getObject("updated_at")).isNull();
                }
                assertThat(count).isEqualTo(2);
            }
            statement.execute("UPDATE disco_qr_copy SET estado = 'REMOVED', disposition_reason = 'DAMAGED' WHERE id = 1");
            assertThat(statement.executeQuery(
                    "SELECT COUNT(*) FROM disco_qr_copy WHERE estado = 'REMOVED' AND disposition_reason = 'DAMAGED'"))
                    .satisfies(result -> {
                        try {
                            assertThat(result.next()).isTrue();
                            assertThat(result.getInt(1)).isEqualTo(1);
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    });
        }
    }

    private void run(Connection connection, Path migration) throws Exception {
        try (var reader = Files.newBufferedReader(migration, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }
}
