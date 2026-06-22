/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.runtime.annotations.RecordableConstructor;

import java.util.List;
import java.util.Objects;

/**
 * Build-time descriptor for a {@code @CasualService} method, used by the recorder to register services at runtime.
 *
 * @param serviceName the Casual service name
 * @param className the fully qualified class name of the bean
 * @param methodName the method name on the bean
 * @param category the service category
 * @param parameterTypes the fully qualified class names of the method parameters
 * @param transactionType the Jakarta transaction type as a string, or null if not annotated
 */
public record CasualServiceDescriptor(String serviceName, String className, String methodName, String category, List<String> parameterTypes,
                                      String transactionType)
{
    @RecordableConstructor
    public CasualServiceDescriptor
    {
        Objects.requireNonNull(serviceName, "serviceName can not be null");
        Objects.requireNonNull(className, "className can not be null");
        Objects.requireNonNull(methodName, "methodName can not be null");
        Objects.requireNonNull(parameterTypes, "parameterTypes can not be null");
    }
}
