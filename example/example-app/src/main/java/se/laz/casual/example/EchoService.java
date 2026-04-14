/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

/**
 * Interface for the echo service
 */
public interface EchoService
{
    /**
     * Echo back the request buffer
     * @param request the inbound request
     * @return the inbound response with the same buffer
     */
    InboundResponse echo(InboundRequest request);
}
