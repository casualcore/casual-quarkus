/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus

import se.laz.casual.config.ConfigurationOptions
import se.laz.casual.config.ConfigurationService
import se.laz.casual.config.ReverseOutbound
import se.laz.casual.jca.CasualManagedConnectionFactory
import spock.lang.Specification

class CasualQuarkusResourceAdapterFactoryTest extends Specification
{
    private static final String ADAPTER_ID = 'secondary'
    private final CasualQuarkusResourceAdapterFactory factory = new CasualQuarkusResourceAdapterFactory()
    private List<ReverseOutbound> previousReverseOutbounds

    def setup()
    {
        previousReverseOutbounds = ConfigurationService.getConfiguration(
                ConfigurationOptions.CASUAL_REVERSE_OUTBOUND_INSTANCES)
        ConfigurationService.setConfiguration(ConfigurationOptions.CASUAL_REVERSE_OUTBOUND_INSTANCES, [])
    }

    def cleanup()
    {
        ConfigurationService.setConfiguration(
                ConfigurationOptions.CASUAL_REVERSE_OUTBOUND_INSTANCES, previousReverseOutbounds)
    }

    def 'validated configuration is applied to managed connection factory'()
    {
        given:
        def config = [
                'host'                        : 'localhost',
                'port'                        : '7771',
                'network-connection-pool-name': 'casual-pool',
                'network-connection-pool-size': '1'
        ]
        def adapter = factory.createResourceAdapter(ADAPTER_ID, config)

        when:
        CasualManagedConnectionFactory result = factory.createManagedConnectionFactory(ADAPTER_ID, adapter)

        then:
        result.hostName == 'localhost'
        result.portNumber == 7771
        result.networkConnectionPoolName == 'casual-pool'
        result.networkConnectionPoolSize == 1
        result.resourceAdapter.is(adapter)
    }
}
