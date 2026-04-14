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

    public void registerServices(BeanContainer beanContainer, List<CasualServiceDescriptor> descriptors) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        CasualQuarkusServiceRegistry registry = beanContainer.beanInstance(CasualQuarkusServiceRegistry.class);
        int registered = 0;
        LOG.log(Logger.Level.INFO, () -> "=== Casual Quarkus Service Registration: Starting ===");
        for (CasualServiceDescriptor descriptor : descriptors) {
            try {
                Class<?> beanClass = cl.loadClass(descriptor.className());
                Object beanInstance = beanContainer.beanInstance(beanClass);

                // Load each parameter class to get the exact Method handle
                Class<?>[] paramTypes = new Class[descriptor.parameterTypes().size()];
                for (int i = 0; i < descriptor.parameterTypes().size(); i++) {
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
                LOG.log(Logger.Level.INFO, () -> "Registered service: " + descriptor.serviceName()
                        + " -> " + descriptor.className() + "." + descriptor.methodName() + "()");
            }
            catch (Exception e)
            {
                LOG.log(Logger.Level.ERROR, "Failed to register: " + descriptor.serviceName(), e);
            }
        }
    }

    private Class<?> loadClass(ClassLoader cl, String name) throws ClassNotFoundException
    {
        return switch (name)
        {
            // Primitives
            case "byte"    -> byte.class;
            case "int"     -> int.class;
            case "long"    -> long.class;
            case "double"  -> double.class;
            case "boolean" -> boolean.class;
            case "char"    -> char.class;
            case "float"   -> float.class;
            case "short"   -> short.class;
            case "void"    -> void.class;

            // Common Primitive Arrays
            case "byte[]", "[B" -> byte[].class;
            case "int[]",  "[I" -> int[].class;

            // Fallback for objects and object arrays
            default -> {
                if (name.endsWith("[]")) {
                    // Convert "com.foo.Bar[]" to "[Lcom.foo.Bar;" for Class.forName
                    String elementClassName = name.substring(0, name.length() - 2);
                    Class<?> elementClass = loadClass(cl, elementClassName);
                    yield java.lang.reflect.Array.newInstance(elementClass, 0).getClass();
                }
                yield cl.loadClass(name);
            }
        };
    }
}
