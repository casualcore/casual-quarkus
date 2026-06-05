/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.TransactionState;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.api.service.ServiceInfo;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandler;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandlerFactory;
import se.laz.casual.jca.inbound.handler.buffer.InboundRequestInfo;
import se.laz.casual.jca.inbound.handler.buffer.ServiceCallInfo;
import se.laz.casual.jca.inbound.handler.service.ServiceHandler;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtensionContext;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtensionFactory;
import se.laz.casual.spi.Priority;

import java.lang.System.Logger;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Quarkus-specific service handler that uses CDI beans instead of JNDI lookups.
 * This handler integrates with Casual's inbound infrastructure while using Quarkus's CDI.
 *
 * Note: This class is instantiated by ServiceLoader (SPI), so it cannot use CDI injection.
 * It accesses the registry via a static reference.
 */
public class CasualQuarkusServiceHandler implements ServiceHandler
{
    private interface CasualMarkerInterface {}
    private static final Logger LOG = System.getLogger(CasualQuarkusServiceHandler.class.getName());
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger(0);

    public static boolean hasInFlight()
    {
        return IN_FLIGHT.get() > 0;
    }

    public static int inFlightCount()
    {
        return IN_FLIGHT.get();
    }

    /**
     * Higher priority than default CasualServiceHandler (LEVEL_5)
     * so that Quarkus services are preferred over JNDI-based services.
     */
    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_4;
    }

    @Override
    public boolean canHandleService(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        if (registry != null)
        {
            return registry.hasService(serviceName);
        }
        LOG.log(ERROR, () -> "Registry is NULL! Cannot handle service: " + serviceName);
        return false;
    }

    @Override
    public boolean isServiceAvailable(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        return registry != null && registry.hasService(serviceName);
    }

    @Override
    public InboundResponse invokeService(InboundRequest request)
    {
        IN_FLIGHT.incrementAndGet();
        var contextClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(CasualQuarkusServiceHandler.class.getClassLoader());
            String serviceName = request.getServiceName();
            CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
            if (registry == null)
            {
                LOG.log(ERROR, () -> "CasualQuarkusServiceRegistry not initialized!");
                return InboundResponse.createBuilder()
                                      .errorState(ErrorState.TPESYSTEM)
                                      .transactionState(TransactionState.ROLLBACK_ONLY)
                                      .build();
            }

            ServiceEntry serviceEntry = registry.getService(serviceName);

            if (serviceEntry == null)
            {
                LOG.log(WARNING, () -> "Service not found: " + serviceName);
                return InboundResponse.createBuilder()
                                      .errorState(ErrorState.TPENOENT)
                                      .transactionState(TransactionState.ROLLBACK_ONLY)
                                      .build();
            }

            ServiceHandlerExtension serviceHandlerExtension = ServiceHandlerExtensionFactory.getExtension(CasualService.class.getName());
            ServiceHandlerExtensionContext extensionContext = null;
            try
            {
                Object beanInstance = serviceEntry.beanInstance();
                Method method = serviceEntry.method();

                LOG.log(Logger.Level.TRACE, () -> "Calling " + beanInstance.getClass().getSimpleName()
                        + "." + method.getName() + "()");

                BufferHandler bufferHandler = BufferHandlerFactory.getHandler(request.getBuffer().getType());
                extensionContext = serviceHandlerExtension.before(request, bufferHandler);

                Proxy dummyProxy = (Proxy) Proxy.newProxyInstance(
                        Thread.currentThread().getContextClassLoader(),
                        new Class<?>[]{CasualMarkerInterface.class},
                        (proxy, m, args) -> null
                );

                InboundRequestInfo requestInfo = InboundRequestInfo.createBuilder()
                                                                   .withProxy(dummyProxy)
                                                                   .withProxyMethod(method)
                                                                   .withRealMethod(method)
                                                                   .withServiceName(serviceName)
                                                                   .build();
                ServiceCallInfo info = bufferHandler.fromRequest(requestInfo, request);
                Object[] params = serviceHandlerExtension.convertRequestParams(extensionContext, info.getParams());
                Object result = method.invoke(beanInstance, params);
                return serviceHandlerExtension.handleSuccess(extensionContext, bufferHandler.toResponse(info, result));
            }
            catch (Exception e)
            {
                LOG.log(ERROR, "Error invoking service " + serviceName, e);
                InboundResponse response = InboundResponse.createBuilder()
                                                          .errorState(ErrorState.TPESVCERR)
                                                          .transactionState(TransactionState.ROLLBACK_ONLY)
                                                          .build();
                return serviceHandlerExtension.handleError(extensionContext, request, response, e);
            }
            finally
            {
                if (extensionContext != null)
                {
                    serviceHandlerExtension.after(extensionContext);
                }
            }
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            IN_FLIGHT.decrementAndGet();
        }
    }

    @Override
    public ServiceInfo getServiceInfo(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        if (registry == null)
        {
            throw new IllegalStateException("CasualQuarkusServiceRegistry not initialized");
        }

        ServiceEntry serviceEntry = registry.getService(serviceName);

        if (serviceEntry == null)
        {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }
        return ServiceInfo.of(
                serviceEntry.serviceName(),
                serviceEntry.category(),
                serviceEntry.transactionType()
        );
    }
}
