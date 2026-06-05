/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.node;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import se.laz.casual.api.CasualRuntimeException;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.connection.caller.CasualCaller;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

@ApplicationScoped
public class CounterForwarder
{
    @Inject
    CasualCaller casualCaller;

    @CasualService(name = "counter", category = "node")
    @Transactional(Transactional.TxType.REQUIRED)
    public InboundResponse forward(InboundRequest request)
    {
        ServiceReturn<CasualBuffer> result = casualCaller.tpcall(
                "db_counter", request.getBuffer(), Flag.of(AtmiFlags.NOFLAG));
        if (result.getErrorState() != ErrorState.OK)
        {
            throw new CasualRuntimeException("db_counter call failed: " + result.getErrorState());
        }
        return InboundResponse.createBuilder()
                .buffer(result.getReplyBuffer())
                .build();
    }
}
