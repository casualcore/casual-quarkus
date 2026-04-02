/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Represents a registered Casual service in Quarkus.
 */
public record ServiceEntry(String serviceName, String category, Object beanInstance, Method method)
{
    public ServiceEntry
    {
        Objects.requireNonNull(serviceName, "serviceName cannot be null");
        Objects.requireNonNull(beanInstance, "beanInstance cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
    }
    @Override
    public String toString()
    {
        return "ServiceEntry{" +
                "serviceName='" + serviceName + '\'' +
                ", category='" + category + '\'' +
                ", bean=" + beanInstance.getClass().getSimpleName() +
                ", method=" + method.getName() +
                '}';
    }
}
