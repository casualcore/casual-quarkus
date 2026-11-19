/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import se.laz.casual.api.buffer.CasualHeaders;
import se.laz.casual.api.buffer.type.ServiceBuffer;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandler;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtensionContext;
import se.laz.casual.quarkus.CasualQuarkusServiceHandler;
import se.laz.casual.quarkus.CasualShutdownDelayHandler;
import se.laz.casual.spi.Priority;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasualQuarkusServiceHandlerRequestContextTest
{
    private static final String BREADCRUMB_HEADER = "breadcrumb";
    private static final String SERVICE_NAME = "request-context-test";
    private static final Queue<Observation> BEFORE = new ConcurrentLinkedQueue<>();
    private static final Queue<Observation> SERVICE = new ConcurrentLinkedQueue<>();
    private static final Queue<Observation> AFTER = new ConcurrentLinkedQueue<>();
    private static final Queue<UUID> DESTROYED = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean FAIL_SERVICE = new AtomicBoolean();

    @RegisterExtension
    static final QuarkusUnitTest UNIT_TEST = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.arc.exclude-types", CasualShutdownDelayHandler.class.getName())
            .overrideConfigKey("quarkus.log.category.\"se.laz.casual.quarkus.CasualQuarkusServiceHandler\".level",
                    "OFF")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RequestCallMetadata.class, RequestContextService.class,
                            RequestContextServiceHandlerExtension.class, RequestContextExtensionContext.class));

    @Inject
    CasualQuarkusServiceHandler serviceHandler;

    @BeforeEach
    void reset()
    {
        BEFORE.clear();
        SERVICE.clear();
        AFTER.clear();
        DESTROYED.clear();
        FAIL_SERVICE.set(false);
        assertFalse(Arc.container().requestContext().isActive());
    }

    @Test
    void requestScopedMetadataIsSharedDuringOneInvocationAndIsolatedBetweenInvocations()
    {
        UUID firstBreadcrumb = UUID.randomUUID();
        InboundResponse firstResponse = serviceHandler.invokeService(request(firstBreadcrumb));

        assertEquals(ErrorState.OK, firstResponse.getErrorState());
        Invocation firstInvocation = completedInvocation();
        assertEquals(firstBreadcrumb, firstInvocation.before().breadcrumb());
        assertInvocationUsedActiveRequestContext(firstInvocation);
        assertEquals(firstInvocation.before().instanceId(), firstInvocation.service().instanceId());
        assertEquals(firstInvocation.before().instanceId(), firstInvocation.after().instanceId());
        assertTrue(DESTROYED.contains(firstInvocation.before().instanceId()));
        assertEquals(1, DESTROYED.size());
        assertFalse(Arc.container().requestContext().isActive());

        UUID secondBreadcrumb = UUID.randomUUID();
        InboundResponse secondResponse = serviceHandler.invokeService(request(secondBreadcrumb));

        assertEquals(ErrorState.OK, secondResponse.getErrorState());
        Invocation secondInvocation = completedInvocation();
        assertEquals(secondBreadcrumb, secondInvocation.before().breadcrumb());
        assertInvocationUsedActiveRequestContext(secondInvocation);
        assertNotEquals(firstInvocation.before().instanceId(), secondInvocation.before().instanceId());
        assertTrue(DESTROYED.contains(secondInvocation.before().instanceId()));
        assertEquals(2, DESTROYED.size());
        assertFalse(Arc.container().requestContext().isActive());
    }

    @Test
    void requestContextIsTerminatedWhenServiceInvocationFails()
    {
        FAIL_SERVICE.set(true);

        InboundResponse response = serviceHandler.invokeService(request(UUID.randomUUID()));

        assertEquals(ErrorState.TPESVCERR, response.getErrorState());
        Invocation invocation = completedInvocation();
        assertInvocationUsedActiveRequestContext(invocation);
        assertEquals(invocation.before().instanceId(), invocation.after().instanceId());
        assertTrue(DESTROYED.contains(invocation.before().instanceId()));
        assertEquals(1, DESTROYED.size());
        assertFalse(Arc.container().requestContext().isActive());
    }

    private static InboundRequest request(UUID breadcrumb)
    {
        CasualHeaders headers = CasualHeaders.newBuilder()
                                             .add(BREADCRUMB_HEADER, breadcrumb.toString())
                                             .build();
        return InboundRequest.of(SERVICE_NAME, ServiceBuffer.of("test", List.of(), headers));
    }

    private static Invocation completedInvocation()
    {
        Observation before = BEFORE.remove();
        Observation service = SERVICE.remove();
        Observation after = AFTER.remove();
        return new Invocation(before, service, after);
    }

    private static void assertInvocationUsedActiveRequestContext(Invocation invocation)
    {
        assertTrue(invocation.before().requestContextActive());
        assertTrue(invocation.service().requestContextActive());
        assertTrue(invocation.after().requestContextActive());
    }

    private record Observation(UUID instanceId, UUID breadcrumb, boolean requestContextActive)
    {
    }

    private record Invocation(Observation before, Observation service, Observation after)
    {
    }

    private record RequestContextExtensionContext(UUID instanceId)
            implements ServiceHandlerExtensionContext
    {
    }

    @RequestScoped
    public static class RequestCallMetadata
    {
        private final UUID instanceId = UUID.randomUUID();
        private UUID breadcrumb;

        UUID instanceId()
        {
            return instanceId;
        }

        UUID breadcrumb()
        {
            return breadcrumb;
        }

        void setBreadcrumb(UUID breadcrumb)
        {
            this.breadcrumb = breadcrumb;
        }

        @PreDestroy
        void destroyed()
        {
            DESTROYED.add(instanceId);
        }
    }

    @ApplicationScoped
    public static class RequestContextService
    {
        @Inject
        RequestCallMetadata callMetadata;

        @CasualService(name = SERVICE_NAME, category = "test")
        public InboundResponse invoke(InboundRequest request)
        {
            SERVICE.add(observation(callMetadata));
            if (FAIL_SERVICE.get())
            {
                throw new IllegalStateException("Expected test failure");
            }
            return InboundResponse.createBuilder().buffer(request.getBuffer()).build();
        }
    }

    @ApplicationScoped
    public static class RequestContextServiceHandlerExtension implements ServiceHandlerExtension
    {
        @Inject
        RequestCallMetadata callMetadata;

        @Override
        public ServiceHandlerExtensionContext before(InboundRequest request, BufferHandler bufferHandler)
        {
            UUID breadcrumb = UUID.fromString(request.getBuffer().getHeaders().get(BREADCRUMB_HEADER).getFirst());
            callMetadata.setBreadcrumb(breadcrumb);
            BEFORE.add(observation(callMetadata));
            return new RequestContextExtensionContext(callMetadata.instanceId());
        }

        @Override
        public void after(ServiceHandlerExtensionContext context)
        {
            RequestContextExtensionContext extensionContext = (RequestContextExtensionContext) context;
            assertEquals(extensionContext.instanceId(), callMetadata.instanceId());
            AFTER.add(observation(callMetadata));
        }

        @Override
        public boolean canHandle(String name)
        {
            return CasualService.class.getName().equals(name);
        }

        @Override
        public Priority getPriority()
        {
            return Priority.LEVEL_0;
        }
    }

    private static Observation observation(RequestCallMetadata callMetadata)
    {
        return new Observation(callMetadata.instanceId(), callMetadata.breadcrumb(),
                Arc.container().requestContext().isActive());
    }
}
