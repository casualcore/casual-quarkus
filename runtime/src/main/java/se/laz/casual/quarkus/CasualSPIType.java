package se.laz.casual.quarkus;

import java.util.Arrays;
import java.util.Optional;

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
