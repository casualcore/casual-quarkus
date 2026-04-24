/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.System.Logger;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Quarkus-specific service registry that holds CDI bean instances and their service methods.
 */
@ApplicationScoped
public class CasualQuarkusServiceRegistry
{
    private static final Logger LOG = System.getLogger(CasualQuarkusServiceRegistry.class.getName());

    private static CasualQuarkusServiceRegistry instance;

    private static final Map<String, ServiceEntry> services = new ConcurrentHashMap<>();

    public CasualQuarkusServiceRegistry()
    {
        LOG.log(INFO, () -> "=== CasualQuarkusServiceRegistry instance created ===");
    }

    public static CasualQuarkusServiceRegistry getInstance()
    {
        if(null == instance)
        {
            instance = new CasualQuarkusServiceRegistry();
        }
        return instance;
    }

    public void registerService(ServiceEntry entry)
    {
        services.put(entry.serviceName(), entry);
        LOG.log(INFO, () -> "Service registered: " + entry.serviceName() + " -> " + entry.beanInstance().getClass().getSimpleName());
    }

    public ServiceEntry getService(String serviceName)
    {
        return services.get(serviceName);
    }

    public boolean hasService(String serviceName)
    {
        boolean has = services.containsKey(serviceName);
        LOG.log(TRACE, () -> "hasService('" + serviceName + "'): " + has + " (total services: " + services.size() + ")");
        return has;
    }
}
