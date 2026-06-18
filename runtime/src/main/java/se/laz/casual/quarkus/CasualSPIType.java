/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    private static final Map<String, CasualSPIType> CACHE;

    static
    {
        Map<String, CasualSPIType> map = new HashMap<>();
        for (CasualSPIType spiType : CasualSPIType.values()) {
            map.put(spiType.name, spiType);
        }
        CACHE = Collections.unmodifiableMap(map);
    }

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
        Objects.requireNonNull(name, "name can not be null");
        CasualSPIType type = CACHE.get(name);
        if (type == null)
        {
            throw new IllegalArgumentException("Unknown CasualSPIType: " + name);
        }
        return type;
    }
}
