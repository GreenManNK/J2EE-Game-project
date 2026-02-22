package com.caro.game.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

@Configuration
@Profile("!test")
public class AutoDataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(AutoDataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        String host = env.getProperty("app.datasource.laragon.host", "127.0.0.1");
        String port = env.getProperty("app.datasource.laragon.port", "3306");
        String database = env.getProperty("app.datasource.laragon.database", "caro");
        String username = env.getProperty("app.datasource.laragon.username", "root");
        String password = env.getProperty("app.datasource.laragon.password", "");

        String mysqlUrl = String.format(
            "jdbc:mysql://%s:%s/%s?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            host, port, database
        );

        if (canConnect(mysqlUrl, username, password)) {
            log.info("Using Laragon MySQL datasource: {}", mysqlUrl);
            return hikari(mysqlUrl, "com.mysql.cj.jdbc.Driver", username, password);
        }
        throw new IllegalStateException(
            "Cannot connect to Laragon MySQL at " + mysqlUrl
                + ". Please start Laragon/MySQL and verify credentials."
        );
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

    private static DataSource hikari(String url, String driverClassName, String user, String pass) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setDriverClassName(driverClassName);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(15000);
        return new HikariDataSource(config);
    }
}
