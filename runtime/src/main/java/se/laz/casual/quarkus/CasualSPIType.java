/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import java.util.Arrays;
import java.util.Optional;

/**
 * The types of Casual JCA SPIs that can be overridden in a user application.
 */
public enum CasualSPIType
{
    BUFFER_HANDLER("se.laz.casual.jca.inbound.handler.buffer.BufferHandler"),
    SERVICE_HANDLER("se.laz.casual.jca.inbound.handler.service.ServiceHandler"),
    SERVICE_HANDLER_EXTENSION("se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension"),
    FIELDED_MARSHALLER("se.laz.casual.api.buffer.type.fielded.marshalling.FieldedMarshaller");
    private final String name;

    CasualSPIType(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public static CasualSPIType unmarshall(String name)
    {
        Optional<CasualSPIType> t = Arrays.stream(CasualSPIType.values())
                                          .filter(v -> v.getName().equals(name))
                                          .findFirst();
        return t.orElseThrow(() -> new IllegalArgumentException("CasualSPIType:" + name));
    }
}
