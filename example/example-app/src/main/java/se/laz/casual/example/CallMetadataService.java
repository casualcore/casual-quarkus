/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.laz.casual.api.buffer.type.CStringBuffer;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.example.user.handlers.CallMetadata;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

import java.util.UUID;

/**
 * Demonstrates how an inbound Casual service consumes request-scoped call metadata.
 */
@ApplicationScoped
public class CallMetadataService
{
    private final CallMetadata callMetadata;

    @Inject
    CallMetadataService(CallMetadata callMetadata)
    {
        this.callMetadata = callMetadata;
    }

    /**
     * Returns the breadcrumb associated with the current service call.
     *
     * @param request the inbound service request
     * @return a response containing the breadcrumb, or an empty string when no breadcrumb is available
     */
    @CasualService(name = "callMetadata", category = "example")
    @ShouldLog
    public InboundResponse callMetadata(InboundRequest request)
    {
        String breadcrumb = callMetadata.getBreadcrumb()
                                        .map(UUID::toString)
                                        .orElse("");
        return InboundResponse.createBuilder()
                              .buffer(CStringBuffer.of(breadcrumb))
                              .build();
    }
}
