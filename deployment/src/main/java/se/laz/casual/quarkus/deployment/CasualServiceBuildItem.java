/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.builder.item.MultiBuildItem;

import java.util.List;

final class CasualServiceBuildItem extends MultiBuildItem
{
    private final String serviceName;
    private final String className;
    private final String methodName;
    private final String category;
    private final String transactionType;
    private final List<String> parameterTypes;

    CasualServiceBuildItem(String serviceName, String className, String methodName, String category, List<String> parameterTypes, String transactionType)
    {
        this.serviceName = serviceName;
        this.className = className;
        this.methodName = methodName;
        this.category = category;
        this.parameterTypes = parameterTypes;
        this.transactionType = transactionType;
    }
    public String getServiceName() { return serviceName; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getCategory() { return category; }
    public List<String> getParameterTypes() { return parameterTypes; }
    public String getTransactionType() { return transactionType; }
}
