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

class CrmMigrationReplayTest {

    @Test
    void migration038CanBeAppliedTwice() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "..", "docs", "migraciones",
                "038_crm_interes_cliente.sql").normalize();
        assertThat(migration).exists();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:crm-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE cliente (id_cliente BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE venta (id_venta BIGINT PRIMARY KEY, id_cliente BIGINT, estado VARCHAR(30), fecha_venta TIMESTAMP)");
            statement.execute("CREATE TABLE detalle_venta (id_detalle BIGINT PRIMARY KEY, id_venta BIGINT)");
            statement.execute("CREATE TABLE disco_qr_copy (id BIGINT PRIMARY KEY, id_disco BIGINT, estado VARCHAR(30))");

            run(connection, migration);
            run(connection, migration);

            try (ResultSet columns = connection.getMetaData().getColumns(null, null,
                    "CRM_INTERES_CLIENTE", null)) {
                int count = 0;
                while (columns.next()) count++;
                assertThat(count).isEqualTo(6);
            }
            try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null,
                    "CRM_INTERES_CLIENTE", false, false)) {
                assertThat(indexes.next()).isTrue();
            }
        }
    }

    private void run(Connection connection, Path migration) throws Exception {
        try (var reader = Files.newBufferedReader(migration, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }
}
