/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.runtime.annotations.RecordableConstructor;

import java.util.Objects;

public record CasualServiceDescriptor(String serviceName, String className, String methodName, String category)
{
    @RecordableConstructor
    public CasualServiceDescriptor
    {
        Objects.requireNonNull(serviceName, "serviceName can not be null");
        Objects.requireNonNull(className, "className can not be null");
        Objects.requireNonNull(methodName, "methodName can not be null");
    }
}
