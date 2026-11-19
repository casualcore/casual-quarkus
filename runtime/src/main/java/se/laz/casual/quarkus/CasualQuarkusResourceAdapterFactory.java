/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkiverse.ironjacamar.ResourceAdapterFactory;
import io.quarkiverse.ironjacamar.ResourceAdapterKind;
import io.quarkiverse.ironjacamar.ResourceAdapterTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.endpoint.MessageEndpoint;
import se.laz.casual.jca.CasualConnectionFactory;
import se.laz.casual.jca.CasualManagedConnectionFactory;
import se.laz.casual.jca.inflow.CasualActivationSpec;
import se.laz.casual.jca.inflow.CasualMessageListener;
import se.laz.casual.quarkus.validation.CasualQuarkusConfigurationValidator;
import se.laz.casual.quarkus.validation.ValidatedConnectionFactoryConfiguration;

import java.util.Map;

@ApplicationScoped
@ResourceAdapterKind("casual")
@ResourceAdapterTypes(connectionFactoryTypes = { CasualConnectionFactory.class })
/**
 * IronJacamar factory that creates Casual resource adapter, managed connection factory, and activation spec instances.
 */
public class CasualQuarkusResourceAdapterFactory implements ResourceAdapterFactory
{
    @Override
    public ResourceAdapter createResourceAdapter(String id, Map<String, String> config)
    {
        CasualQuarkusResourceAdapter ra = new CasualQuarkusResourceAdapter();
        ra.setConfig(config);
        return ra;
    }

    @Override
    public ManagedConnectionFactory createManagedConnectionFactory(String id, ResourceAdapter adapter) throws ResourceException
    {
        CasualQuarkusResourceAdapter quarkusAdapter = (CasualQuarkusResourceAdapter) adapter;
        Map<String, String> config = quarkusAdapter.getConfig();
        ValidatedConnectionFactoryConfiguration configuration =
                CasualQuarkusConfigurationValidator.validate(id, config);

        CasualManagedConnectionFactory mcf = new CasualManagedConnectionFactory();
        mcf.setHostName(configuration.host());
        mcf.setPortNumber(configuration.port());
        mcf.setNetworkConnectionPoolName(configuration.poolName());
        if (configuration.poolSize() != null)
        {
            mcf.setNetworkConnectionPoolSize(configuration.poolSize());
        }
        mcf.setResourceAdapter(adapter);
        return mcf;
    }

    @Override
    public ActivationSpec createActivationSpec(String id, ResourceAdapter adapter, Class<?> type, Map<String, String> config)
    {
        CasualActivationSpec activationSpec = new CasualActivationSpec();
        activationSpec.setResourceAdapter(adapter);
        if (config != null && config.containsKey("port"))
        {
            activationSpec.setPort(Integer.parseInt(config.get("port")));
        }
        return activationSpec;
    }

    @Override
    public MessageEndpoint wrap(MessageEndpoint messageEndpoint, Object instance)
    {
        return new CasualMessageEndpointWrapper(messageEndpoint, (CasualMessageListener) instance);
    }
}
