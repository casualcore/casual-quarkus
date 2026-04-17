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
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        CasualQuarkusServiceRegistry registry = beanContainer.beanInstance(CasualQuarkusServiceRegistry.class);

        LOG.log(INFO, "=== Casual Quarkus Service Registration: Starting ===");
        int registered = 0;

        for (CasualServiceDescriptor descriptor : descriptors)
        {
            try
            {
                Class<?> beanClass = cl.loadClass(descriptor.className());
                Object beanInstance = beanContainer.beanInstance(beanClass);

                if (beanInstance == null)
                {
                    throw new CasualRuntimeException("CDI Bean not found for class: " + descriptor.className());
                }

                // Resolve parameter types
                Class<?>[] paramTypes = new Class[descriptor.parameterTypes().size()];
                for (int i = 0; i < descriptor.parameterTypes().size(); i++)
                {
                    paramTypes[i] = loadClass(cl, descriptor.parameterTypes().get(i));
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

    private Class<?> loadClass(ClassLoader cl, String name) throws ClassNotFoundException
    {
        return switch (name) {
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "char" -> char.class;
            case "void" -> void.class;
            default -> {
                // Check if it's already an internal JVM descriptor like [Ljava.lang.String;
                if (name.startsWith("["))
                {
                    yield Class.forName(name, false, cl);
                }
                // Handle human-readable array syntax "com.foo.Bar[]"
                if (name.endsWith("[]"))
                {
                    String elementClassName = name.substring(0, name.length() - 2);
                    Class<?> elementClass = loadClass(cl, elementClassName);
                    yield java.lang.reflect.Array.newInstance(elementClass, 0).getClass();
                }
                yield cl.loadClass(name);
            }
        };
    }
}
