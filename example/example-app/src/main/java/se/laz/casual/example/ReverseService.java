/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

/**
 * Interface for the reverse service
 */
public interface ReverseService
{
    /**
     * Reverse the bytes in the request buffer
     * @param request the inbound request
     * @return the inbound response with reversed buffer
     */
    InboundResponse reverse(InboundRequest request);
}
