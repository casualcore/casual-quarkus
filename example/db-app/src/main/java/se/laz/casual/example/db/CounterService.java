/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.db;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class CounterService
{
    @Inject
    DataSource dataSource;

    @CasualService(name = "db_counter", category = "db")
    @Transactional(Transactional.TxType.REQUIRED)
    public InboundResponse incrementCounter(InboundRequest request)
    {
        try (Connection conn = dataSource.getConnection())
        {
            PreparedStatement update = conn.prepareStatement(
                    "UPDATE counter SET \"VALUE\" = \"VALUE\" + 1 WHERE id = 1");
            update.executeUpdate();

            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT \"VALUE\" FROM counter WHERE id = 1");
            rs.next();
            long newValue = rs.getLong(1);

            return InboundResponse.createBuilder()
                    .buffer(OctetBuffer.of(String.valueOf(newValue).getBytes(StandardCharsets.UTF_8)))
                    .build();
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Counter update failed", e);
        }
    }
}
