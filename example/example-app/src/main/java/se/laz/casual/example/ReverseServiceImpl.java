/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.enterprise.context.ApplicationScoped;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

import java.lang.System.Logger;
import java.util.List;

/**
 * Reverse service implementation that reverses the bytes in the buffer
 */
@ApplicationScoped
public class ReverseServiceImpl implements ReverseService
{
    @CasualService(name = "reverse", category = "example")
    @Override
    public InboundResponse reverse(InboundRequest request) {
        List<byte[]> payload = request.getBuffer().getBytes();
        if (payload.isEmpty())
        {
            return InboundResponse.createBuilder().buffer(request.getBuffer()).build();
        }

        byte[] data = payload.get(0);
        byte[] reversed = new byte[data.length];

        // Simple, efficient reversal
        for (int i = 0; i < data.length; i++)
        {
            reversed[i] = data[data.length - 1 - i];
        }

        return InboundResponse.createBuilder()
                              .buffer(OctetBuffer.of(reversed))
                              .build();
    }
}
