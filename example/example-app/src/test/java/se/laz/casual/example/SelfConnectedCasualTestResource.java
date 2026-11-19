/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import se.laz.casual.config.ConfigurationOptions;
import se.laz.casual.config.ConfigurationService;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

/**
 * Configures the example application to call its own Casual inbound listener.
 */
public class SelfConnectedCasualTestResource implements QuarkusTestResourceLifecycleManager
{
    @Override
    public Map<String, String> start()
    {
        // Match the unmanaged executor configuration used by Casual Quarkus applications.
        ConfigurationService.setConfiguration(ConfigurationOptions.CASUAL_UNMANAGED, true);
        ConfigurationService.setConfiguration(ConfigurationOptions.CASUAL_OUTBOUND_UNMANAGED, true);
        String port = Integer.toString(findAvailablePort());
        return Map.of(
                "quarkus.ironjacamar.casual.ra.config.host", "127.0.0.1",
                "quarkus.ironjacamar.casual.ra.config.port", port,
                "quarkus.ironjacamar.casual.ra.config.inbound-server-port", port,
                "quarkus.ironjacamar.casual.ra.config.network-connection-pool-size", "1",
                "quarkus.ironjacamar.casual.ra.cm.pool.config.min-size", "0",
                "quarkus.ironjacamar.casual.ra.cm.pool.config.initial-size", "0",
                "quarkus.ironjacamar.casual.ra.cm.pool.config.max-size", "10",
                "quarkus.shutdown.delay-enabled", "false");
    }

    @Override
    public void stop()
    {
        // The Quarkus test runtime owns all resources.
    }

    private static int findAvailablePort()
    {
        try (ServerSocket serverSocket = new ServerSocket(0))
        {
            return serverSocket.getLocalPort();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to allocate a port for the Casual test listener", e);
        }
    }
}
