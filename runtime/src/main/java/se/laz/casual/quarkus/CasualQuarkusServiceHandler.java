/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.TransactionState;
import se.laz.casual.api.service.ServiceInfo;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.service.ServiceHandler;
import se.laz.casual.network.messages.domain.TransactionType;
import se.laz.casual.spi.Priority;

import java.lang.System.Logger;
import java.lang.reflect.Method;

/**
 * Quarkus-specific service handler that uses CDI beans instead of JNDI lookups.
 * This handler integrates with Casual's inbound infrastructure while using Quarkus's CDI.
 *
 * Note: This class is instantiated by ServiceLoader (SPI), so it cannot use CDI injection.
 * It accesses the registry via a static reference.
 */
public class CasualQuarkusServiceHandler implements ServiceHandler
{
    private static final Logger LOG = System.getLogger(CasualQuarkusServiceHandler.class.getName());

    /**
     * Higher priority than default CasualServiceHandler (LEVEL_5)
     * so that Quarkus services are preferred over JNDI-based services.
     */
    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_3;
    }

    @Override
    public boolean canHandleService(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        if (registry != null)
        {
            return registry.hasService(serviceName);
        }
        LOG.log(Logger.Level.ERROR, () -> "Registry is NULL! Cannot handle service: " + serviceName);
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
        String serviceName = request.getServiceName();
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        if (registry == null)
        {
            LOG.log(Logger.Level.ERROR, () -> "CasualQuarkusServiceRegistry not initialized!");
            return InboundResponse.createBuilder()
                    .errorState(ErrorState.TPESYSTEM)
                    .transactionState(TransactionState.ROLLBACK_ONLY)
                    .build();
        }

        ServiceEntry serviceEntry = registry.getService(serviceName);

        if (serviceEntry == null)
        {
            LOG.log(Logger.Level.WARNING, () -> "Service not found: " + serviceName);
            return InboundResponse.createBuilder()
                    .errorState(ErrorState.TPENOENT)
                    .transactionState(TransactionState.ROLLBACK_ONLY)
                    .build();
        }

        try
        {
            Object beanInstance = serviceEntry.beanInstance();
            Method method = serviceEntry.method();

            LOG.log(Logger.Level.TRACE,() -> "Calling " + beanInstance.getClass().getSimpleName()
                + "." + method.getName() + "()");

            Object result = method.invoke(beanInstance, request);

            if (result instanceof InboundResponse inboundResponse)
            {
                LOG.log(Logger.Level.TRACE, () -> "Service " + serviceName + " completed successfully");
                return inboundResponse;
            }
            else
            {
                LOG.log(Logger.Level.WARNING, () -> "Service " + serviceName + " did not return InboundResponse, got: "
                    + (result != null ? result.getClass() : "null"));
                return InboundResponse.createBuilder()
                        .errorState(ErrorState.TPESVCERR)
                        .transactionState(TransactionState.ROLLBACK_ONLY)
                        .build();
            }
        }
        catch (Exception e)
        {
            LOG.log(Logger.Level.ERROR, "Error invoking service " + serviceName, e);
            return InboundResponse.createBuilder()
                    .errorState(ErrorState.TPESVCERR)
                    .transactionState(TransactionState.ROLLBACK_ONLY)
                    .build();
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
        // for now, default to AUTO transaction type
        // in the future, this could be determined from annotations
        return ServiceInfo.of(
            serviceEntry.serviceName(),
            serviceEntry.category(),
            TransactionType.AUTOMATIC
        );
    }
}
