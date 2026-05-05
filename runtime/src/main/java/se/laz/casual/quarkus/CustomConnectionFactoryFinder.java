

package se.laz.casual.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.jca.core.api.management.ConnectionFactory;
import se.laz.casual.api.CasualRuntimeException;
import se.laz.casual.connection.caller.ConnectionFactoryEntry;
import se.laz.casual.connection.caller.ConnectionFactoryFinder;
import se.laz.casual.connection.caller.ConnectionFactoryProducer;
import se.laz.casual.jca.CasualConnectionFactory;

import java.util.List;


@ApplicationScoped
@Alternative
@Priority(1)
public class CustomConnectionFactoryFinder implements ConnectionFactoryFinder
{
    private static final System.Logger LOG = System.getLogger(CustomConnectionFactoryFinder.class.getName());

    // get all configured outbound pools
    @Inject
    @Any
    Instance<CasualConnectionFactory> connectionFactories;

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
                                                              .orElseThrow(() -> new CasualRuntimeException("Pool name is missing for: " + handle.get() + ""));
                                      CasualConnectionFactory cf = handle.get();
                                      return createConnectionFactoryEntry(cf, poolName);
                                  })
                                  .toList();
    }

    private ConnectionFactoryEntry createConnectionFactoryEntry(CasualConnectionFactory cf, String poolName)
    {
        ConnectionFactoryProducer producer = new ConnectionFactoryProducer(){
            @Override
            public String getUniqueName()
            {
                return poolName;
            }
            @Override
            public CasualConnectionFactory getConnectionFactory()
            {
                return cf;
            }
        };
        return ConnectionFactoryEntry.of(producer);
    }
}
