/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import se.laz.casual.api.CasualRuntimeException;

import java.lang.System.Logger;
import java.lang.reflect.Method;
import java.util.List;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * Uses the services found at build time to register in the CasualQuarkusServiceRegistry
 */
@Recorder
public class CasualServiceRecorder
{
    private static final Logger LOG = System.getLogger(CasualServiceRecorder.class.getName());

    public void registerServices(BeanContainer beanContainer, List<CasualServiceDescriptor> descriptors)
    {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        CasualQuarkusServiceRegistry registry = beanContainer.beanInstance(CasualQuarkusServiceRegistry.class);

        LOG.log(INFO, "=== Casual Quarkus Service Registration: Starting ===");
        int registered = 0;

        for (CasualServiceDescriptor descriptor : descriptors)
        {
            try
            {
                Class<?> beanClass = contextClassLoader.loadClass(descriptor.className());
                Object beanInstance = beanContainer.beanInstance(beanClass);

                if (beanInstance == null)
                {
                    throw new CasualRuntimeException("CDI Bean not found for class: " + descriptor.className());
                }

                // Resolve parameter types
                Class<?>[] paramTypes = new Class[descriptor.parameterTypes().size()];
                for (int i = 0; i < descriptor.parameterTypes().size(); i++)
                {
                    paramTypes[i] = CasualTypeResolver.loadClass(contextClassLoader, descriptor.parameterTypes().get(i));
                }

                Method method = beanClass.getMethod(descriptor.methodName(), paramTypes);

                registry.registerService(
                        descriptor.serviceName(),
                        descriptor.category(),
                        beanInstance,
                        method
                );

                registered++;
                LOG.log(INFO, "Registered: {0} -> {1}.{2}()",
                        descriptor.serviceName(), descriptor.className(), descriptor.methodName());

            }
            catch (Throwable e)
            {
                LOG.log(ERROR, "Failed to register Casual service: " + descriptor.serviceName(), e);
            }
        }
        LOG.log(INFO, "Successfully registered {0} services", registered);
    }
}
