/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import se.laz.casual.jca.CasualResourceAdapter;

import javax.transaction.xa.XAResource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.System.Logger;

/**
 * Wrapper for CasualResourceAdapter
 *
 * Quarkus IronJacamar creates one RA per outbound pool config.
 * For inbound we only ever want to start one inbound server.
 * The static AtomicInteger guard ensures only the first RA instance (CasualQuarkusResourceAdapter from ironjacamars point of view)
 * activates the inbound endpoint and only the last deactivation shuts it down.
 */
public class CasualQuarkusResourceAdapter implements ResourceAdapter
{
    private static final Logger LOG = System.getLogger(CasualQuarkusResourceAdapter.class.getName());
    private static final AtomicInteger INBOUND_ACTIVE = new AtomicInteger(0);
    // We only ever want to create one real RA
    // This since it sets up the event server, inbound and reverse inbound
    // All which should be done only once
    // Quarkus Ironjacamar creates one CasualQuarkusResourceAdapter per configured outbound pool
    private static final CasualResourceAdapter delegate = new CasualResourceAdapter();
    private Map<String, String> config;
    // static since stop can be called on any of n number or RA instances
    private static ActivationSpec activationSpec;
    private static MessageEndpointFactory endpointFactory;

    public Map<String, String> getConfig()
    {
        return config;
    }

    public void setConfig(Map<String, String> config)
    {
        this.config = config;
    }

    @Override
    public void start(BootstrapContext ctx) throws ResourceAdapterInternalException
    {
        LOG.log(Logger.Level.INFO, () -> "CasualQuarkusResourceAdapter.start() called");
        if (null != config && config.containsKey("inbound-server-port"))
        {
            Integer port = Integer.parseInt(config.get("inbound-server-port"));
            LOG.log(Logger.Level.INFO, () -> "Setting inbound server port to: " + port);
            delegate.setInboundServerPort(port);
        }
        delegate.start(ctx);
    }

    @Override
    public void stop()
    {
        // endpointActivation is not called for some reason
        // we really want to do that before stopping the RA
        endpointDeactivation(endpointFactory, activationSpec);
        delegate.stop();
    }

    @Override
    public void endpointActivation(MessageEndpointFactory factory, ActivationSpec spec)
            throws ResourceException
    {
        // we guard so that this is only being executed once - regardless how many outbound pools
        // that are configured
        if (INBOUND_ACTIVE.getAndIncrement() == 0)
        {
            LOG.log(Logger.Level.INFO, () -> "Activating inbound endpoint (first RA instance). spec:" + spec);
            setActivationSpec(spec);
            setEndpointFactory(factory);
            delegate.endpointActivation(factory, spec);
        }
    }

    @Override
    public void endpointDeactivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
    {
        // we guard so that this is only being executed once - regardless how many outbound pools
        // that are configured
        if (INBOUND_ACTIVE.decrementAndGet() == 0)
        {
            LOG.log(Logger.Level.INFO, () -> "Deactivating inbound endpoint");
            delegate.endpointDeactivation(endpointFactory, activationSpec);
        }
    }

    @Override
    public XAResource[] getXAResources(ActivationSpec[] specs) throws ResourceException
    {
        return delegate.getXAResources(specs);
    }

    private static void setActivationSpec(ActivationSpec spec)
    {
        activationSpec = spec;
    }

    private static void setEndpointFactory(MessageEndpointFactory factory)
    {
        endpointFactory = factory;
    }

}
