/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import se.laz.casual.jca.inbound.handler.InboundRequest;

import java.lang.System.Logger;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Uses the services found at build time to register in the CasualQuarkusServiceRegistry
 */
@Recorder
public class CasualServiceRecorder
{
    private static final Logger LOG = System.getLogger(CasualServiceRecorder.class.getName());

    public void registerServices(BeanContainer beanContainer, List<CasualServiceDescriptor> descriptors)
    {
        LOG.log(Logger.Level.INFO, () -> "=== Casual Quarkus Service Registration: Starting ===");
        CasualQuarkusServiceRegistry registry = beanContainer.beanInstance(CasualQuarkusServiceRegistry.class);
        int registered = 0;
        for (CasualServiceDescriptor descriptor : descriptors)
        {
            try
            {
                Class<?> beanClass = Thread.currentThread().getContextClassLoader().loadClass(descriptor.className());
                Object beanInstance = beanContainer.beanInstance(beanClass);
                Method method = beanClass.getMethod(descriptor.methodName(), InboundRequest.class);
                registry.registerService(descriptor.serviceName(), descriptor.category(), beanInstance, method);
                registered++;
                LOG.log(Logger.Level.INFO, () -> "Registered service: " + descriptor.serviceName()
                        + " -> " + descriptor.className() + "." + descriptor.methodName() + "()");
            }
            catch (Exception e)
            {
                LOG.log(Logger.Level.ERROR, "Failed to register service " + descriptor.serviceName() + ": " + e.getMessage(), e);
            }
        }
        LOG.log(Logger.Level.INFO, "=== Casual Quarkus Service Registration: Complete ({0} services) ===", registered);
    }
}
