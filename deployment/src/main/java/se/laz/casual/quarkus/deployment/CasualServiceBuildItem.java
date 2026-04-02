/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.builder.item.MultiBuildItem;

final class CasualServiceBuildItem extends MultiBuildItem
{
    private final String serviceName;
    private final String className;
    private final String methodName;
    private final String category;

    CasualServiceBuildItem(String serviceName, String className, String methodName, String category)
    {
        this.serviceName = serviceName;
        this.className = className;
        this.methodName = methodName;
        this.category = category;
    }

    public String getServiceName() { return serviceName; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getCategory() { return category; }
}
