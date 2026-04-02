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
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.endpoint.MessageEndpoint;
import se.laz.casual.jca.CasualConnectionFactory;
import se.laz.casual.jca.CasualManagedConnectionFactory;
import se.laz.casual.jca.inflow.CasualActivationSpec;
import se.laz.casual.jca.inflow.CasualMessageListener;

import java.util.Map;

@ApplicationScoped
@ResourceAdapterKind("casual")
@ResourceAdapterTypes(connectionFactoryTypes = { CasualConnectionFactory.class })
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
    public ManagedConnectionFactory createManagedConnectionFactory(String id, ResourceAdapter adapter)
    {
        CasualQuarkusResourceAdapter quarkusAdapter = (CasualQuarkusResourceAdapter) adapter;
        CasualManagedConnectionFactory mcf = new CasualManagedConnectionFactory();
        Map<String, String> config = quarkusAdapter.getConfig();
        mcf.setHostName(config.get("host"));
        mcf.setPortNumber(Integer.parseInt(config.get("port")));
        if (config.containsKey("network-connection-pool-size")) {
            mcf.setNetworkConnectionPoolSize(Integer.parseInt(config.get("network-connection-pool-size")));
        }
        if (config.containsKey("network-connection-pool-name")) {
            mcf.setNetworkConnectionPoolName(config.get("network-connection-pool-name"));
        }
        mcf.setResourceAdapter(adapter);
        return mcf;
    }

    @Override
    public ActivationSpec createActivationSpec(String id, ResourceAdapter adapter, Class<?> type, Map<String, String> config)
    {
        CasualActivationSpec activationSpec = new CasualActivationSpec();
        activationSpec.setResourceAdapter(adapter);
        if (config != null && config.containsKey("port")) {
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
