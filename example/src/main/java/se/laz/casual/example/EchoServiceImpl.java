/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.enterprise.context.ApplicationScoped;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

/**
 * Echo service implementation that echoes back the received buffer
 */
@ApplicationScoped
public class EchoServiceImpl implements EchoService
{
    @CasualService(name = "casual/example/java/echo", category = "example")
    @Override
    public InboundResponse echo(InboundRequest request)
    {
        return InboundResponse.createBuilder()
                .buffer(request.getBuffer())
                .build();
    }
}
