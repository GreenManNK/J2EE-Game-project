package com.game.hub.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
@Profile("!test")
public class AutoDataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(AutoDataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        String kind = normalizeKind(env.getProperty("app.datasource.kind", "auto"));
        boolean allowH2Fallback = Boolean.parseBoolean(
            env.getProperty("app.datasource.auto.allow-h2-fallback", "false")
        );
        String h2File = env.getProperty("app.datasource.auto.h2-file", ".data/game-public");
        String h2Username = env.getProperty("app.datasource.auto.h2-username", "sa");
        String h2Password = env.getProperty("app.datasource.auto.h2-password", "");

        String explicitSpringUrl = trimToNull(env.getProperty("spring.datasource.url"));
        if (explicitSpringUrl != null) {
            String explicitUser = env.getProperty("spring.datasource.username", "");
            String explicitPass = env.getProperty("spring.datasource.password", "");
            String explicitDriver = trimToNull(env.getProperty("spring.datasource.driver-class-name"));
            if (explicitDriver == null) {
                explicitDriver = inferDriverClassName(explicitSpringUrl);
            }
            return connectPreferredOrFallback(
                "spring.datasource.url",
                explicitSpringUrl,
                explicitDriver,
                explicitUser,
                explicitPass,
                allowH2Fallback,
                h2File,
                h2Username,
                h2Password,
                "Please verify SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD."
            );
        }

        DbCandidate legacyLaragonMysql = mysqlCandidate(
            "Laragon MySQL (legacy)",
            env.getProperty("app.datasource.laragon.host", "127.0.0.1"),
            env.getProperty("app.datasource.laragon.port", "3306"),
            env.getProperty("app.datasource.laragon.database", "caro"),
            env.getProperty("app.datasource.laragon.username", "root"),
            env.getProperty("app.datasource.laragon.password", "")
        );
        DbCandidate standardMysql = mysqlCandidate(
            "MySQL",
            env.getProperty("app.datasource.mysql.host", "127.0.0.1"),
            env.getProperty("app.datasource.mysql.port", "3306"),
            env.getProperty("app.datasource.mysql.database", "caro"),
            env.getProperty("app.datasource.mysql.username", "root"),
            env.getProperty("app.datasource.mysql.password", "")
        );
        DbCandidate postgres = postgresCandidate(
            env.getProperty("app.datasource.postgres.host", "127.0.0.1"),
            env.getProperty("app.datasource.postgres.port", "5432"),
            env.getProperty("app.datasource.postgres.database", "caro"),
            env.getProperty("app.datasource.postgres.username", "postgres"),
            env.getProperty("app.datasource.postgres.password", ""),
            env.getProperty("app.datasource.postgres.schema", ""),
            env.getProperty("app.datasource.postgres.ssl-mode", "disable")
        );

        switch (kind) {
            case "laragon":
                return connectPreferredOrFallback(
                    legacyLaragonMysql.label,
                    legacyLaragonMysql.url,
                    legacyLaragonMysql.driverClassName,
                    legacyLaragonMysql.username,
                    legacyLaragonMysql.password,
                    allowH2Fallback,
                    h2File,
                    h2Username,
                    h2Password,
                    "Please start Laragon/MySQL and verify LARAGON_DB_* variables."
                );
            case "mysql":
                return connectPreferredOrFallback(
                    standardMysql.label,
                    standardMysql.url,
                    standardMysql.driverClassName,
                    standardMysql.username,
                    standardMysql.password,
                    allowH2Fallback,
                    h2File,
                    h2Username,
                    h2Password,
                    "Please start MySQL server and verify APP_DATASOURCE_MYSQL_* (or legacy LARAGON_DB_*)."
                );
            case "postgres":
            case "postgresql":
                return connectPreferredOrFallback(
                    postgres.label,
                    postgres.url,
                    postgres.driverClassName,
                    postgres.username,
                    postgres.password,
                    allowH2Fallback,
                    h2File,
                    h2Username,
                    h2Password,
                    "Please start PostgreSQL and verify APP_DATASOURCE_POSTGRES_* (database should already exist)."
                );
            case "h2":
                return h2DataSource(h2File, h2Username, h2Password, "Forced H2 by APP_DATASOURCE_KIND=h2");
            case "auto":
            default:
                DataSource connected = tryConnect(legacyLaragonMysql);
                if (connected != null) {
                    return connected;
                }
                if (!sameTarget(legacyLaragonMysql, standardMysql)) {
                    connected = tryConnect(standardMysql);
                    if (connected != null) {
                        return connected;
                    }
                }
                if (allowH2Fallback) {
                    return h2DataSource(h2File, h2Username, h2Password,
                        "MySQL not available on auto candidates. Falling back to local H2 file datasource");
                }
                throw new IllegalStateException(
                    "Cannot connect to any auto datasource candidate (Laragon MySQL: " + legacyLaragonMysql.url
                        + ", MySQL: " + standardMysql.url + "). "
                        + "Please start MySQL and verify credentials, or set APP_DATASOURCE_KIND=postgres with "
                        + "APP_DATASOURCE_POSTGRES_*, or enable APP_DATASOURCE_ALLOW_H2_FALLBACK=true."
                );
        }
    }

    private static boolean canConnect(String url, String user, String pass) {
        try {
            DriverManager.setLoginTimeout(2);
            try (Connection ignored = DriverManager.getConnection(url, user, pass)) {
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
    }

    private DataSource connectPreferredOrFallback(String label,
                                                  String url,
                                                  String driverClassName,
                                                  String user,
                                                  String pass,
                                                  boolean allowH2Fallback,
                                                  String h2File,
                                                  String h2Username,
                                                  String h2Password,
                                                  String failHint) {
        DataSource dataSource = tryConnect(new DbCandidate(label, url, driverClassName, user, pass));
        if (dataSource != null) {
            return dataSource;
        }
        if (allowH2Fallback) {
            return h2DataSource(
                h2File,
                h2Username,
                h2Password,
                label + " not available at " + url + ". Falling back to local H2 file datasource"
            );
        }
        throw new IllegalStateException(
            "Cannot connect to " + label + " at " + url + ". " + failHint
                + " Or enable APP_DATASOURCE_ALLOW_H2_FALLBACK=true."
        );
    }

    private DataSource tryConnect(DbCandidate candidate) {
        if (canConnect(candidate.url, candidate.username, candidate.password)) {
            log.info("Using {} datasource: {}", candidate.label, candidate.url);
            return hikari(candidate.url, candidate.driverClassName, candidate.username, candidate.password);
        }
        return null;
    }

    private DataSource h2DataSource(String h2File, String h2Username, String h2Password, String reason) {
        String h2Url = buildH2FileUrl(h2File);
        log.warn("{}: {}", reason, h2Url);
        return hikari(h2Url, "org.h2.Driver", h2Username, h2Password);
    }

    private static DbCandidate mysqlCandidate(String label, String host, String port, String database, String username, String password) {
        String mysqlUrl = String.format(
            "jdbc:mysql://%s:%s/%s?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            host, port, database
        );
        return new DbCandidate(label, mysqlUrl, "com.mysql.cj.jdbc.Driver", username, password);
    }

    private static DbCandidate postgresCandidate(String host,
                                                 String port,
                                                 String database,
                                                 String username,
                                                 String password,
                                                 String schema,
                                                 String sslMode) {
        List<String> params = new ArrayList<>();
        String sslModeValue = trimToNull(sslMode);
        params.add("sslmode=" + (sslModeValue == null ? "disable" : sslModeValue));
        String schemaValue = trimToNull(schema);
        if (schemaValue != null) {
            params.add("currentSchema=" + schemaValue);
        }
        StringBuilder url = new StringBuilder("jdbc:postgresql://")
            .append(host).append(':').append(port).append('/').append(database);
        if (!params.isEmpty()) {
            url.append('?').append(String.join("&", params));
        }
        return new DbCandidate("PostgreSQL", url.toString(), "org.postgresql.Driver", username, password);
    }

    private static boolean sameTarget(DbCandidate a, DbCandidate b) {
        return a.url.equals(b.url)
            && safeValue(a.username, "").equals(safeValue(b.username, ""));
    }

    private static DataSource hikari(String url, String driverClassName, String user, String pass) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        if (driverClassName != null && !driverClassName.isBlank()) {
            config.setDriverClassName(driverClassName);
        }
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(15000);
        return new HikariDataSource(config);
    }

    private static String buildH2FileUrl(String h2FilePath) {
        try {
            Path filePath = Paths.get(h2FilePath).toAbsolutePath().normalize();
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String normalized = filePath.toString().replace("\\", "/");
            return "jdbc:h2:file:" + normalized
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot prepare local H2 file datasource path: " + h2FilePath, ex);
        }
    }

    private static String inferDriverClassName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (lower.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        return null;
    }

    private static String normalizeKind(String value) {
        String normalized = safeValue(value, "auto").trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "auto" : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeValue(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private record DbCandidate(String label, String url, String driverClassName, String username, String password) {
    }
}
