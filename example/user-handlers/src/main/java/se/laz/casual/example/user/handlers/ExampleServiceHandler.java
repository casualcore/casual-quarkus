/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.user.handlers;

import se.laz.casual.api.service.ServiceInfo;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.service.ServiceHandler;
import se.laz.casual.quarkus.CasualQuarkusServiceHandler;
import se.laz.casual.spi.Priority;

import java.lang.System.Logger;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.getLogger;

public class ExampleServiceHandler implements ServiceHandler
{
    private static final Logger LOG = getLogger(ExampleServiceHandler.class.getName());
    private static final CasualQuarkusServiceHandler delegate = new CasualQuarkusServiceHandler();
    @Override
    public boolean canHandleService(String serviceName)
    {
        LOG.log(INFO, () -> "ExampleServiceHandler::canHandleService " + serviceName);
        return delegate.canHandleService(serviceName);
    }

    @Override
    public boolean isServiceAvailable(String serviceName)
    {
        LOG.log(INFO, () -> "ExampleServiceHandler::isServiceAvailable " + serviceName);
        return delegate.isServiceAvailable(serviceName);
    }

    @Override
    public InboundResponse invokeService(InboundRequest request)
    {
        LOG.log(INFO, () -> "ExampleServiceHandler::invokeService " + request);
        return delegate.invokeService(request);
    }

    @Override
    public ServiceInfo getServiceInfo(String serviceName)
    {
        LOG.log(INFO, () -> "ExampleServiceHandler::getServiceInfo " + serviceName);
        return delegate.getServiceInfo(serviceName);
    }

    @Override
    public Priority getPriority()
    {
        // lower than CasualQuarkusServiceHandler
        return Priority.LEVEL_0;
    }
}
