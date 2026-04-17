/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.user.handlers;

import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandler;
import se.laz.casual.jca.inbound.handler.buffer.InboundRequestInfo;
import se.laz.casual.jca.inbound.handler.buffer.ServiceCallInfo;
import se.laz.casual.jca.inbound.handler.buffer.fielded.FieldedBufferHandler;
import se.laz.casual.spi.Priority;

import static java.lang.System.Logger.Level.INFO;

public class ExampleFieldedBufferHandler implements BufferHandler
{
    private static final System.Logger LOG = System.getLogger(ExampleFieldedBufferHandler.class.getName());
    private static final FieldedBufferHandler delegate = new FieldedBufferHandler();

    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_4;
    }

    @Override
    public boolean canHandleBuffer(String bufferType)
    {
        LOG.log(INFO, () -> "ExampleFieldedBufferHandler::canHandleBuffer " + bufferType);
        return delegate.canHandleBuffer(bufferType);
    }

    @Override
    public ServiceCallInfo fromRequest(InboundRequestInfo requestInfo, InboundRequest request)
    {
        LOG.log(INFO, () -> "ExampleFieldedBufferHandler::fromRequest " + requestInfo + " " + request);
        return delegate.fromRequest(requestInfo, request);
    }

    @Override
    public InboundResponse toResponse(ServiceCallInfo info, Object result)
    {
        LOG.log(INFO, () -> "ExampleFieldedBufferHandler::toResponse " + info + " " + result);
        return delegate.toResponse(info,result);
    }
}



