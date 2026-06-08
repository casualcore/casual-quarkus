/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.runtime.ShutdownDelayInitiatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import se.laz.casual.jca.CasualResourceManager;
import se.laz.casual.jca.Predicate;
import se.laz.casual.jca.RuntimeInformation;
import se.laz.casual.jca.ShutdownBarrier;
import se.laz.casual.jca.inflow.CasualInboundTransactionRegistry;
import se.laz.casual.network.InboundDeactivatedContext;
import se.laz.casual.network.InboundTopologyUpdateContext;

/**
 * Observes Quarkus shutdown delay to perform early graceful shutdown of casual inbound.
 *
 * When {@code quarkus.shutdown.delay-enabled=true} and {@code quarkus.shutdown.delay} is configured,
 * this handler fires before the actual CDI/IronJacamar teardown begins. It:
 * <ol>
 *   <li>Sets the domain-shutdown flag - any outbound service/queue calls returns TPENOENT</li>
 *   <li>Sends domain-disconnect to all connected casual clients so they stop routing traffic here</li>
 *   <li>A small wait period to allow clients to handle the domain disconnect message</li>
 *   <li>Waits for pending inbound XA transactions and pending outbound XA transactions to complete</li>
 * </ol>
 */
@ApplicationScoped
public class CasualShutdownDelayHandler
{
    private static final System.Logger LOG = System.getLogger(CasualShutdownDelayHandler.class.getName());

    @Inject
    @ConfigProperty(name = "casual.shutdown.drain-poll-interval-ms", defaultValue = "200")
    long pollIntervalMs;

    @Inject
    @ConfigProperty(name = "casual.shutdown.wire-settle-delay-ms", defaultValue = "1500")
    long wireSettleDelayMs;

    void onShutdown(@Observes ShutdownDelayInitiatedEvent event)
    {
        LOG.log(System.Logger.Level.INFO, "Shutdown initiated: beginning casual graceful shutdown");

        // 1. Domaim going down, no new outbound service calls will be allowed
        //    They will all return TPENOENT
        RuntimeInformation.setDomainIsBeingShutdown(true);

        // 2. Notify connected clients immediately so they stop routing traffic here
        //    besides XA calls (for service/queue calls already in flight)
        InboundDeactivatedContext.domainDisconnect();
        InboundDeactivatedContext.clear();
        InboundTopologyUpdateContext.clear();

        // 3. Wait for the network wire to settle and late packets to land.
        //    This gives the connected clients time to process the disconnect.
        try
        {
            Thread.sleep(wireSettleDelayMs);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        // 4. Drain current in flight work
        Predicate workIsPending = () -> CasualInboundTransactionRegistry.hasPending()
                || CasualResourceManager.getInstance().hasPending();
        ShutdownBarrier shutdownBarrier = ShutdownBarrier.of(pollIntervalMs, workIsPending);
        shutdownBarrier.intermittentSleep();
        LOG.log(System.Logger.Level.INFO, () -> "Casual graceful shutdown complete");
    }
}
