package com.sonograma.repository;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class ManualDiscogsSourceReconciliationMigrationTest {

    @Test
    void migration053IsReplaySafeAndDoesNotInventHistoricalExpectationsOrSnapshots() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "..", "docs", "migraciones",
                "053_manual_discogs_source_reconciliation.sql").normalize();
        assertThat(migration).exists();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:manual-source-reconciliation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE discogs_manual_batch (
                      id_discogs_manual_batch BIGINT PRIMARY KEY,
                      customer_code VARCHAR(255) NOT NULL,
                      normalized_customer_code VARCHAR(255) NOT NULL)
                    """);
            statement.execute("""
                    INSERT INTO discogs_manual_batch
                    (id_discogs_manual_batch, customer_code, normalized_customer_code)
                    VALUES (6, 'TESTSOURCE', 'TESTSOURCE')
                    """);

            run(connection, migration);
            run(connection, migration);

            var reconciliation = statement.executeQuery(
                    "SELECT COUNT(*) FROM manual_discogs_source_reconciliation");
            assertThat(reconciliation.next()).isTrue();
            assertThat(reconciliation.getInt(1)).isZero();
            var snapshots = statement.executeQuery(
                    "SELECT COUNT(*) FROM manual_discogs_finalization_snapshot");
            assertThat(snapshots.next()).isTrue();
            assertThat(snapshots.getInt(1)).isZero();
        }
    }

    private void run(Connection connection, Path migration) throws Exception {
        try (var reader = Files.newBufferedReader(migration, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }
}
