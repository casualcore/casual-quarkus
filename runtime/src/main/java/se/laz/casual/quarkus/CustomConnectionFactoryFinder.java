/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.ConnectionFactoryEntry;
import se.laz.casual.connection.caller.ConnectionFactoryFinder;
import se.laz.casual.jca.CasualConnectionFactory;
import java.util.List;

@ApplicationScoped
@Alternative
@Priority(5)
public class CustomConnectionFactoryFinder implements ConnectionFactoryFinder
{
    private static final System.Logger LOG = System.getLogger(CustomConnectionFactoryFinder.class.getName());

    @Any
    Instance<CasualConnectionFactory> connectionFactories;

    @Inject
    public CustomConnectionFactoryFinder(Instance<CasualConnectionFactory> connectionFactories)
    {
        this.connectionFactories = connectionFactories;
    }

    @Override
    public List<ConnectionFactoryEntry> findConnectionFactory(String root)
    {
        LOG.log(System.Logger.Level.INFO, () -> "CustomConnectionFactoryFinder::findConnectionFactory " + root);
        return connectionFactories.handlesStream()
                                  .map(handle -> {
                                      String poolName = handle.getBean().getQualifiers().stream()
                                                              .filter(q -> q.annotationType().equals(io.smallrye.common.annotation.Identifier.class))
                                                              .map(q -> ((io.smallrye.common.annotation.Identifier) q).value())
                                                              .findFirst()
                                                              .orElseThrow(() -> new CasualConnectionFactoryException("Pool name is missing for: " + handle.get()));
                                      CasualConnectionFactory cf = handle.get();
                                      return ConnectionFactoryEntry.of(QuarkusConnectionFactoryProducer.of(poolName, cf));
                                  })
                                  .toList();
    }
}
