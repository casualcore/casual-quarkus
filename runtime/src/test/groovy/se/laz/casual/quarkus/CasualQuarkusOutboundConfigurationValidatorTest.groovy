/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus

import io.quarkiverse.ironjacamar.Defaults
import jakarta.resource.spi.InvalidPropertyException
import se.laz.casual.config.ConfigurationOptions
import se.laz.casual.config.ConfigurationService
import se.laz.casual.config.ReverseOutbound
import se.laz.casual.quarkus.validation.CasualQuarkusOutboundConfigurationValidator
import spock.lang.Specification

class CasualQuarkusOutboundConfigurationValidatorTest extends Specification
{
    private static final String ADAPTER_ID = 'secondary'
    private static final String HOST = 'host'
    private static final String PORT = 'port'
    private static final String POOL_NAME = 'network-connection-pool-name'
    private static final String POOL_SIZE = 'network-connection-pool-size'

    private List<ReverseOutbound> previousReverseOutbounds

    def 'rejects a Casual adapter without an explicit name: #id'()
    {
        when:
        CasualQuarkusOutboundConfigurationValidator.validate(id, normalConfiguration())

        then:
        InvalidPropertyException exception = thrown()
        exception.message.contains('requires an explicit name')

        where:
        id << [null, '', ' ', Defaults.DEFAULT_RESOURCE_ADAPTER_NAME]
    }

    def 'normal outbound configuration is validated and converted: #id'()
    {
        given:
        def config = normalConfiguration()

        when:
        def result = CasualQuarkusOutboundConfigurationValidator.validate(id, config)

        then:
        result.host() == config[HOST]
        result.port() == Integer.parseInt(config[PORT])
        result.poolName() == config[POOL_NAME]
        result.poolSize() == Integer.parseInt(config[POOL_SIZE])

        where:
        id << ['casual', ADAPTER_ID]
    }

    def 'reverse outbound configuration does not require pool size: #size'()
    {
        given:
        String reversePoolName = 'reverse-pool'
        int port = 7785
        ConfigurationService.setConfiguration(ConfigurationOptions.CASUAL_REVERSE_OUTBOUND_INSTANCES,
                [ReverseOutbound.newBuilder().withName(reversePoolName).withPort(port).build()])
        def config = normalConfiguration()
        config[POOL_NAME] = reversePoolName
        if (size == null)
        {
            config.remove(POOL_SIZE)
        }
        else
        {
            config[POOL_SIZE] = size
        }

        when:
        def result = CasualQuarkusOutboundConfigurationValidator.validate(ADAPTER_ID, config)

        then:
        result.poolName() == reversePoolName
        result.poolSize() == expectedSize

        where:
        size | expectedSize
        null | null
        '0'  | 0
        '-1' | -1
        '1'  | 1
    }

    def 'missing or blank mandatory property is rejected: #key=#value'()
    {
        given:
        def config = normalConfiguration()
        if (value == null)
        {
            config.remove(key)
        }
        else
        {
            config[key] = value
        }

        when:
        CasualQuarkusOutboundConfigurationValidator.validate(ADAPTER_ID, config)

        then:
        InvalidPropertyException exception = thrown()
        exception.message.contains("quarkus.ironjacamar.${ADAPTER_ID}.ra.config.${key}")
        exception.message.endsWith('is missing or blank')

        where:
        key       | value
        HOST      | null
        HOST      | ' '
        PORT      | null
        PORT      | ' '
        POOL_NAME | null
        POOL_NAME | ' '
        POOL_SIZE | null
        POOL_SIZE | ' '
    }

    def 'invalid integer property is rejected: #key=#value'()
    {
        given:
        def config = normalConfiguration()
        config[key] = value

        when:
        CasualQuarkusOutboundConfigurationValidator.validate(ADAPTER_ID, config)

        then:
        InvalidPropertyException exception = thrown()
        exception.message == "Invalid Casual resource adapter '${ADAPTER_ID}': property " +
                "'quarkus.ironjacamar.${ADAPTER_ID}.ra.config.${key}' must be an integer but was '${value}'"
        exception.cause instanceof NumberFormatException

        where:
        key       | value
        PORT      | 'seven'
        POOL_SIZE | 'one'
    }

    def 'nonpositive normal outbound pool size is rejected: #size'()
    {
        given:
        def config = normalConfiguration()
        config[POOL_SIZE] = size

        when:
        CasualQuarkusOutboundConfigurationValidator.validate(ADAPTER_ID, config)

        then:
        InvalidPropertyException exception = thrown()
        exception.message.endsWith("must be a positive integer but was '${size}'")

        where:
        size << ['0', '-1']
    }

    private static Map<String, String> normalConfiguration()
    {
        return [
                (HOST)     : 'localhost',
                (PORT)     : '7771',
                (POOL_NAME): 'casual-pool',
                (POOL_SIZE): '1'
        ]
    }
}
