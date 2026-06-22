/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class CasualSpiBuildItem extends MultiBuildItem
{
    private final String serviceInterface;
    private final String implementationClass;

    public CasualSpiBuildItem(String serviceInterface, String implementationClass)
    {
        this.serviceInterface = serviceInterface;
        this.implementationClass = implementationClass;
    }

    public String getServiceInterface()
    {
        return serviceInterface;
    }

    public String getImplementationClass()
    {
        return implementationClass;
    }
}
