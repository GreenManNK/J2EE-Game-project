package com.game.hub.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDataSourceConfigTest {

    @Test
    void shouldFallbackToH2WhenPreferredDatasourceIsUnavailable() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("app.datasource.kind", "mysql")
            .withProperty("app.datasource.mysql.host", "127.0.0.1")
            .withProperty("app.datasource.mysql.port", "65000")
            .withProperty("app.datasource.mysql.database", "missing")
            .withProperty("app.datasource.mysql.username", "root")
            .withProperty("app.datasource.mysql.password", "")
            .withProperty("app.datasource.auto.allow-h2-fallback", "true")
            .withProperty("app.datasource.auto.h2-file", "target/test-h2-fallback/game-public");

        DataSource dataSource = new AutoDataSourceConfig().dataSource(env);
        HikariDataSource hikariDataSource = assertInstanceOf(HikariDataSource.class, dataSource);
        try {
            assertTrue(hikariDataSource.getJdbcUrl().startsWith("jdbc:h2:file:"));
        } finally {
            hikariDataSource.close();
        }
    }
}
