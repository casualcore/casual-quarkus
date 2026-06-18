/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.arc.Arc;
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

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Quarkus-specific service handler that uses CDI beans instead of JNDI lookups.
 * This handler integrates with Casual's inbound infrastructure while using Quarkus's CDI.
 *
 * Note: This class is instantiated by ServiceLoader (SPI), so it cannot use CDI injection.
 * It accesses the registry via Arc.container().
 */
public class CasualQuarkusServiceHandler implements ServiceHandler
{
    private interface CasualMarkerInterface {}
    private static final Logger LOG = System.getLogger(CasualQuarkusServiceHandler.class.getName());

    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_4;
    }

    @Override
    public boolean canHandleService(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = getRegistry();
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
        CasualQuarkusServiceRegistry registry = getRegistry();
        return registry != null && registry.hasService(serviceName);
    }

    @Override
    public InboundResponse invokeService(InboundRequest request)
    {
        var contextClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(CasualQuarkusServiceHandler.class.getClassLoader());
            String serviceName = request.getServiceName();
            CasualQuarkusServiceRegistry registry = getRegistry();
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

                // InboundRequestInfo requires a proxy instance for compatibility with the
                // WildFly EJB-based service handler where the proxy is a real EJB reference.
                // In Quarkus we invoke CDI beans directly, so the proxy is unused — we provide
                // a no-op placeholder to satisfy the builder contract.
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
        }
    }

    @Override
    public ServiceInfo getServiceInfo(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = getRegistry();
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

    private CasualQuarkusServiceRegistry getRegistry()
    {
        var handle = Arc.container().instance(CasualQuarkusServiceRegistry.class);
        return handle.isAvailable() ? handle.get() : null;
    }
}
