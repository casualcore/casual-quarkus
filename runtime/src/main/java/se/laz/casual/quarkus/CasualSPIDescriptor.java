package se.laz.casual.quarkus;

import java.util.Objects;

public record CasualSPIDescriptor(CasualSPIType spiType, String implementationClass)
{
    public CasualSPIDescriptor
    {
        Objects.requireNonNull(spiType, "serviceInterface can not be null");
        Objects.requireNonNull(implementationClass, "implementationClass can not be null");
    }
}
