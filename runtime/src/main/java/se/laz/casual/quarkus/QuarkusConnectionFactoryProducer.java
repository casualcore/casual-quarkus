/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import se.laz.casual.connection.caller.ConnectionFactoryProducer;
import se.laz.casual.jca.CasualConnectionFactory;

import java.util.Objects;

/**
 * Wraps a Quarkus-managed {@link CasualConnectionFactory} with its pool name for use by casual-caller.
 */
public class QuarkusConnectionFactoryProducer implements ConnectionFactoryProducer
{
    private final String name;
    private final CasualConnectionFactory connectionFactory;

    private QuarkusConnectionFactoryProducer(String name, CasualConnectionFactory connectionFactory)
    {
        this.name = name;
        this.connectionFactory = connectionFactory;
    }

    public static QuarkusConnectionFactoryProducer of(String name, CasualConnectionFactory connectionFactory)
    {
        Objects.requireNonNull(name, "name can not be null");
        Objects.requireNonNull(connectionFactory, "connectionFactory can not be null");
        return new QuarkusConnectionFactoryProducer(name, connectionFactory);
    }

    @Override
    public String getUniqueName()
    {
        return name;
    }

    @Override
    public CasualConnectionFactory getConnectionFactory()
    {
        return connectionFactory;
    }

    @Override
    public String toString()
    {
        return "QuarkusConnectionFactoryProducer{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public final boolean equals(Object o)
    {
        if (!(o instanceof QuarkusConnectionFactoryProducer that))
        {
            return false;
        }
        return Objects.equals(that.name, name);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(name);
    }
}
