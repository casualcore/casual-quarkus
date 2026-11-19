/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.validation;

import io.quarkiverse.ironjacamar.Defaults;
import jakarta.resource.spi.InvalidPropertyException;
import se.laz.casual.config.ConfigurationOptions;
import se.laz.casual.config.ConfigurationService;

import java.util.Map;

/**
 * Validates and converts Casual resource-adapter configuration.
 *
 * <p>This class is stateless and thread-safe.</p>
 */
public final class CasualQuarkusConfigurationValidator
{
    private static final String CONFIGURATION_PREFIX = "quarkus.ironjacamar.";
    private static final String CONFIGURATION_SUFFIX = "ra.config.";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String NETWORK_CONNECTION_POOL_NAME = "network-connection-pool-name";
    private static final String NETWORK_CONNECTION_POOL_SIZE = "network-connection-pool-size";

    private CasualQuarkusConfigurationValidator()
    {
    }

    /**
     * Validates and converts configuration for a Casual managed connection factory.
     *
     * @param id resource-adapter identifier
     * @param config effective resource-adapter configuration
     * @return validated managed connection factory configuration
     * @throws InvalidPropertyException if a required property is missing or a property value is invalid
     */
    public static ValidatedConnectionFactoryConfiguration validate(String id, Map<String, String> config)
            throws InvalidPropertyException
    {
        String host = requiredValue(id, config, HOST);
        int port = requiredInteger(id, config, PORT);
        String poolName = requiredValue(id, config, NETWORK_CONNECTION_POOL_NAME);
        Integer poolSize;
        if (isReversePool(poolName))
        {
            poolSize = optionalInteger(id, config, NETWORK_CONNECTION_POOL_SIZE);
        }
        else
        {
            poolSize = requiredPositiveInteger(id, config, NETWORK_CONNECTION_POOL_SIZE);
        }
        return new ValidatedConnectionFactoryConfiguration(host, port, poolName, poolSize);
    }

    private static String requiredValue(String id, Map<String, String> config, String key)
            throws InvalidPropertyException
    {
        if (config == null)
        {
            throw invalidProperty(id, key, "is missing because the resource adapter has no configuration");
        }
        String value = config.get(key);
        if (value == null || value.isBlank())
        {
            throw invalidProperty(id, key, "is missing or blank");
        }
        return value;
    }

    private static int requiredInteger(String id, Map<String, String> config, String key)
            throws InvalidPropertyException
    {
        String value = requiredValue(id, config, key);
        return parseInteger(id, key, value);
    }

    private static int requiredPositiveInteger(String id, Map<String, String> config, String key)
            throws InvalidPropertyException
    {
        int value = requiredInteger(id, config, key);
        if (value <= 0)
        {
            throw invalidProperty(id, key, "must be a positive integer but was '" + value + "'");
        }
        return value;
    }

    private static Integer optionalInteger(String id, Map<String, String> config, String key)
            throws InvalidPropertyException
    {
        String value = config.get(key);
        return value == null ? null : parseInteger(id, key, value);
    }

    private static int parseInteger(String id, String key, String value) throws InvalidPropertyException
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            throw new InvalidPropertyException(invalidPropertyMessage(id, key,
                    "must be an integer but was '" + value + "'"), e);
        }
    }

    private static boolean isReversePool(String poolName)
    {
        return ConfigurationService.getConfiguration(ConfigurationOptions.CASUAL_REVERSE_OUTBOUND_INSTANCES)
                .stream()
                .anyMatch(reverseOutbound -> reverseOutbound.getName().equals(poolName));
    }

    private static InvalidPropertyException invalidProperty(String id, String key, String problem)
    {
        return new InvalidPropertyException(invalidPropertyMessage(id, key, problem));
    }

    private static String invalidPropertyMessage(String id, String key, String problem)
    {
        String adapterPrefix = Defaults.DEFAULT_RESOURCE_ADAPTER_NAME.equals(id) ? "" : id + ".";
        String property = CONFIGURATION_PREFIX + adapterPrefix + CONFIGURATION_SUFFIX + key;
        return "Invalid Casual resource adapter '" + id + "': property '" + property + "' " + problem;
    }


}
