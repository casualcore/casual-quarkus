/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import se.laz.casual.connection.caller.ConnectionValidator;

import static java.lang.System.Logger.Level.TRACE;

/**
 * Timer for validating connection factory entries.
 * The timer defaults 1s and can be configured with the casual.validation.interval property.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class QuarkusConnectionFactoryEntryValidationTimer
{
    private static final System.Logger LOG = System.getLogger(QuarkusConnectionFactoryEntryValidationTimer.class.getName());

    @Inject
    ConnectionValidator connectionValidator;

    @Scheduled(every = "${casual.validation.interval:1s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void validateConnectionFactories()
    {
        LOG.log(TRACE, "Running ConnectionFactoryEntryValidationTimer");
        try
        {
            connectionValidator.validateAllConnections();
        }
        catch (Exception e)
        {
            LOG.log(System.Logger.Level.WARNING, () -> "failed validating connection factories: " + e);
        }
    }
}

