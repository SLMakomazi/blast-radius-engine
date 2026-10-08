package com.madlanga.blastradius.telemetry.provider.health;

import com.madlanga.blastradius.telemetry.config.TelemetryConfig.DatabaseHealthProperties;
import com.madlanga.blastradius.telemetry.model.*;
import java.sql.*;
import java.time.Instant;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DatabaseHealthAdapterTest {
    @Test void directDatabaseProbeDistinguishesUnavailableAvailableAndConfigurationError() throws Exception {
        var connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        class TestDriver implements Driver {
            String failure;
            public Connection connect(String url, Properties p) throws SQLException {
                if (!acceptsURL(url)) return null;
                if (failure != null) throw new SQLException("Test database condition", failure);
                return connection;
            }
            public boolean acceptsURL(String url) { return url.equals("jdbc:outage-test:db"); }
            public DriverPropertyInfo[] getPropertyInfo(String url, Properties p) { return new DriverPropertyInfo[0]; }
            public int getMajorVersion() { return 1; }
            public int getMinorVersion() { return 0; }
            public boolean jdbcCompliant() { return false; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
        }
        var driver = new TestDriver();
        DriverManager.registerDriver(driver);
        int previousTimeout = DriverManager.getLoginTimeout();
        try {
            var settings = new DatabaseHealthProperties();
            settings.setService("ledger-db"); settings.setUrl("jdbc:outage-test:db");
            var adapter = new DatabaseHealthAdapter(settings);
            Instant now = Instant.now();
            var query = TelemetryQuery.builder().applicationId("ledger").environment("test").from(now.minusSeconds(20)).to(now).build();
            driver.failure = "08001";
            assertThat(adapter.fetchHealth(query).getState()).isEqualTo(HealthState.DOWN);
            driver.failure = null;
            assertThat(adapter.fetchHealth(query).getState()).isEqualTo(HealthState.UP);
            driver.failure = "28P01";
            assertThat(adapter.fetchHealth(query).getState()).isEqualTo(HealthState.UNKNOWN);
        } finally {
            DriverManager.deregisterDriver(driver);
            DriverManager.setLoginTimeout(previousTimeout);
        }
    }
}
