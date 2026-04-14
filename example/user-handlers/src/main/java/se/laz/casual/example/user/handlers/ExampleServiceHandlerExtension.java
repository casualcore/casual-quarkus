/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.user.handlers;

import jakarta.enterprise.context.ApplicationScoped;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandler;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtensionContext;
import se.laz.casual.spi.Priority;

import java.lang.System.Logger;

import static java.lang.System.Logger.Level.INFO;

@ApplicationScoped
public class ExampleServiceHandlerExtension implements ServiceHandlerExtension
{
    private static final Logger LOG = System.getLogger(ExampleServiceHandlerExtension.class.getName());
    @Override
    public ServiceHandlerExtensionContext before(InboundRequest request, BufferHandler bufferHandler)
    {
        LOG.log(INFO, () -> "ExampleServiceHandlerExtension::before " + request + " " + bufferHandler);
        return new ExampleServiceHandlerExtensionContext();
    }

    @Override
    public Object[] convertRequestParams(ServiceHandlerExtensionContext context, Object[] params)
    {
        LOG.log(INFO, () -> "ExampleServiceHandlerExtension::convertRequestParams " + context + " " + params);
        return ServiceHandlerExtension.super.convertRequestParams(context, params);
    }

    @Override
    public void after(ServiceHandlerExtensionContext context)
    {
        LOG.log(INFO, () -> "ExampleServiceHandlerExtension::after " + context);
        ServiceHandlerExtension.super.after(context);
    }

    @Override
    public InboundResponse handleError(ServiceHandlerExtensionContext context, InboundRequest request, InboundResponse response, Throwable e)
    {
        LOG.log(INFO, () -> "ExampleServiceHandlerExtension::handleError " + context + " " + request + " " + response + " " + e);
        return ServiceHandlerExtension.super.handleError(context, request, response, e);
    }

    @Override
    public InboundResponse handleSuccess(ServiceHandlerExtensionContext context, InboundResponse response)
    {
        LOG.log(INFO, () -> "ExampleServiceHandlerExtension::handleSuccess " + context + " " + response);
        return ServiceHandlerExtension.super.handleSuccess(context, response);
    }

    @Override
    public boolean canHandle(String name)
    {
        LOG.log(INFO, () -> "ExampleServiceHandlerExtension::canHandle " + name);
        return CasualService.class.getName().equals(name);
    }

    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_4;
    }
}
