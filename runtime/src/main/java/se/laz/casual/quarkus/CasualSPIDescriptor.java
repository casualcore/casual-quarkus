/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
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
