/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.enterprise.context.ApplicationScoped;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.api.buffer.type.fielded.FieldedTypeBuffer;
import se.laz.casual.jca.inbound.handler.InboundResponse;

/**
 * Fielded service implementation that echoes back the received fielded buffer
 */
@ApplicationScoped
public class FieldedServiceImpl implements FieldedService
{
    @CasualService(name = "echoFielded", category = "example")
    @Override
    public SimpleObject echoFielded(SimpleObject data)
    {
        return data;
    }
}
