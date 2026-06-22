/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.db;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

@ApplicationScoped
public class DbInit
{
    private static final Logger LOG = Logger.getLogger(DbInit.class.getName());

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev)
    {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement())
        {
            stmt.execute("CREATE TABLE IF NOT EXISTS counter (id INT PRIMARY KEY, \"VALUE\" BIGINT NOT NULL)");
            stmt.execute("MERGE INTO counter KEY(id) VALUES (1, 0)");
            LOG.info("Counter table initialized");
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Failed to initialize counter table", e);
        }
    }
}
