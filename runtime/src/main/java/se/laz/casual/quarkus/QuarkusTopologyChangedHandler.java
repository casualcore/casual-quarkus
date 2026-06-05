/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.CacheRepopulator;
import se.laz.casual.connection.caller.ConnectionFactoryEntry;
import se.laz.casual.connection.caller.DomainIdChecker;
import se.laz.casual.connection.caller.TopologyChangedHandler;
import se.laz.casual.jca.DomainId;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

@ApplicationScoped
@Alternative
@Priority(5)
public class QuarkusTopologyChangedHandler implements TopologyChangedHandler
  {
      private static final System.Logger LOG = System.getLogger(QuarkusTopologyChangedHandler.class.getName());

      private final Set<DomainId> pendingDomains = ConcurrentHashMap.newKeySet();
      private final CacheRepopulator cacheRepopulator;
      private volatile Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier;

      @Inject
      public QuarkusTopologyChangedHandler(CacheRepopulator cacheRepopulator)
      {
          this.cacheRepopulator = cacheRepopulator;
      }

      @Override
      public void setSupplier(Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier)
      {
          LOG.log(INFO, "Setting connection factory entry supplier");
          this.connectionFactoryEntrySupplier = connectionFactoryEntrySupplier;
      }

      @Override
      public void topologyChanged(DomainId domainId)
      {
          LOG.log(INFO, "topologyChanged: " + domainId);
          pendingDomains.add(domainId);
      }

      @Scheduled(every = "${casual.topology-change.delay:1s}",
                 concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
      void processTopologyChanges()
      {
          if (pendingDomains.isEmpty())
          {
              return;
          }
          for (DomainId domainId : Set.copyOf(pendingDomains))
          {
              try
              {
                  connectionFactoryEntrySupplier.get().stream()
                                                .filter(entry -> DomainIdChecker.isSameDomain(domainId, entry))
                                                .findFirst()
                                                .ifPresent(cacheRepopulator::repopulate);
              }
              catch (Exception e)
              {
                  LOG.log(WARNING, () ->
                      "Failed topology update for domain: " + domainId + ", will retry next cycle - " + e);
              }
              finally
              {
                  pendingDomains.remove(domainId);
              }
          }
      }
  }


