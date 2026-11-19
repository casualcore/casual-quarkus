/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.user.handlers;

import org.junit.jupiter.api.Test;
import se.laz.casual.api.buffer.CasualHeaders;
import se.laz.casual.api.buffer.type.ServiceBuffer;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.buffer.PassThroughBufferHandler;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallMetadataPropagationTest
{
    @Test
    void serviceHandlerExtensionExtractsBreadcrumbFromInboundHeaders()
    {
        UUID breadcrumb = UUID.randomUUID();
        CallMetadata callMetadata = new CallMetadata();
        ExampleServiceHandlerExtension extension = new ExampleServiceHandlerExtension();
        extension.setCallMetadata(callMetadata);
        InboundRequest request = requestWithBreadcrumb(breadcrumb);

        extension.before(request, new PassThroughBufferHandler());

        assertEquals(breadcrumb, callMetadata.getBreadcrumb().orElseThrow());
    }

    @Test
    void missingBreadcrumbLeavesCallMetadataEmpty()
    {
        CallMetadata callMetadata = new CallMetadata();
        ExampleServiceHandlerExtension extension = new ExampleServiceHandlerExtension();
        extension.setCallMetadata(callMetadata);
        InboundRequest request = InboundRequest.of("callMetadata", ServiceBuffer.empty());

        extension.before(request, new PassThroughBufferHandler());

        assertTrue(callMetadata.getBreadcrumb().isEmpty());
    }

    private static InboundRequest requestWithBreadcrumb(UUID breadcrumb)
    {
        CasualHeaders headers = CasualHeaders.newBuilder()
                                             .add(CallMetadata.BREADCRUMB_HEADER, breadcrumb.toString())
                                             .build();
        return InboundRequest.of("callMetadata", ServiceBuffer.of("test", List.of(), headers));
    }
}
