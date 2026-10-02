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

class ManualDiscogsOperationLineageMigrationTest {

    @Test
    void migration052IsReplaySafeAndDoesNotGuessHistoricalLineage() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "..", "docs", "migraciones",
                "052_manual_discogs_operation_lineage.sql").normalize();
        assertThat(migration).exists();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:manual-operation-lineage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE discogs_manual_batch (
                      id_discogs_manual_batch BIGINT PRIMARY KEY,
                      normalized_customer_code VARCHAR(255) NOT NULL)
                    """);
            statement.execute("CREATE TABLE disco_qr_copy (id BIGINT PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE manual_discogs_import_operation (
                      operation_id UUID PRIMARY KEY,
                      discogs_release_id BIGINT NOT NULL,
                      requested_copies INTEGER NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      resulting_product_id BIGINT,
                      result_type VARCHAR(40),
                      available_copies INTEGER,
                      created_at TIMESTAMP NOT NULL,
                      updated_at TIMESTAMP NOT NULL)
                    """);
            statement.execute("""
                    INSERT INTO manual_discogs_import_operation
                    (operation_id, discogs_release_id, requested_copies, status, created_at, updated_at)
                    VALUES ('00000000-0000-0000-0000-000000000001', 123, 1, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);

            run(connection, migration);
            run(connection, migration);

            try (ResultSet row = statement.executeQuery("""
                    SELECT id_discogs_manual_batch, source_customer_code,
                           normalized_source_customer_code, submitted_price,
                           submitted_condition, duplicate_override,
                           duplicate_override_reason, completed_at, abandoned_at
                    FROM manual_discogs_import_operation
                    WHERE operation_id = '00000000-0000-0000-0000-000000000001'
                    """)) {
                assertThat(row.next()).isTrue();
                for (int index = 1; index <= 9; index++) assertThat(row.getObject(index)).isNull();
            }
            assertThat(statement.executeQuery("SELECT COUNT(*) FROM manual_discogs_import_operation_copy"))
                    .satisfies(result -> {
                        try {
                            assertThat(result.next()).isTrue();
                            assertThat(result.getInt(1)).isZero();
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
