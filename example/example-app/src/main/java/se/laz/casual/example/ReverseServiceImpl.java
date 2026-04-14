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

/**
 * Reverse service implementation that reverses the bytes in the buffer
 */
@ApplicationScoped
public class ReverseServiceImpl implements ReverseService
{
    private static final Logger log = System.getLogger(ReverseServiceImpl.class.getName());

    @CasualService(name = "reverse", category = "example")
    @Override
    public InboundResponse reverse(InboundRequest request)
    {
        log.log(Logger.Level.INFO, () -> "Reverse service called with service name: " + request.getServiceName());

        byte[] originalBytes = request.getBuffer().getBytes().get(0);
        byte[] reversedBytes = new byte[originalBytes.length];

        for (int i = 0; i < originalBytes.length; i++)
        {
            reversedBytes[i] = originalBytes[originalBytes.length - 1 - i];
        }

        OctetBuffer reversedBuffer = OctetBuffer.of(reversedBytes);

        return InboundResponse.createBuilder()
                .buffer(reversedBuffer)
                .build();
    }
}
