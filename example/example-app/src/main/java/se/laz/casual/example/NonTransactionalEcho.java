/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

@ApplicationScoped
public class NonTransactionalEcho implements EchoService
{
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    @CasualService(name = "nonTransactionalEcho", category = "example")
    @Override
    public InboundResponse echo(InboundRequest request)
    {
        return InboundResponse.createBuilder()
                              .buffer(request.getBuffer())
                              .build();

    }
}
