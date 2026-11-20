/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus

import jakarta.resource.spi.InvalidPropertyException
import se.laz.casual.jca.CasualManagedConnectionFactory
import spock.lang.Specification

class CasualQuarkusResourceAdapterFactoryTest extends Specification
{
    private static final String ADAPTER_ID = 'casual'
    private final CasualQuarkusResourceAdapterFactory factory = new CasualQuarkusResourceAdapterFactory()

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

    def 'managed connection factory creation rejects an adapter with a missing pool name'()
    {
        given:
        def adapter = factory.createResourceAdapter(ADAPTER_ID, [
                'host'                        : 'localhost',
                'port'                        : '7771',
                'network-connection-pool-size': '1'
        ])

        when:
        factory.createManagedConnectionFactory(ADAPTER_ID, adapter)

        then:
        InvalidPropertyException exception = thrown()
        exception.message.contains("quarkus.ironjacamar.${ADAPTER_ID}.ra.config.network-connection-pool-name")
    }
}
