/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import se.laz.casual.api.buffer.type.CStringBuffer;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.quarkus.CasualQuarkusServiceHandler;
import se.laz.casual.quarkus.CasualShutdownDelayHandler;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CasualServiceInterceptorTest
{
    @RegisterExtension
    static final QuarkusUnitTest UNIT_TEST = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.arc.exclude-types", CasualShutdownDelayHandler.class.getName())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(EchoService.class, ShouldLog.class, LoggingInterceptor.class));

    @Inject
    CasualQuarkusServiceHandler handler;

    @Test
    void interceptsOnlyTheBoundServiceMethod()
    {
        LoggingInterceptor.calls.set(0);
        assertEcho("plain-echo");
        assertEquals(0, LoggingInterceptor.calls.get());
        assertEcho("logged-echo");
        assertEquals(1, LoggingInterceptor.calls.get());
    }

    private void assertEcho(String service)
    {
        InboundResponse response = handler.invokeService(InboundRequest.of(service, CStringBuffer.of(service)));
        assertEquals(ErrorState.OK, response.getErrorState());
        assertEquals(service, CStringBuffer.of(response.getBuffer().getBytes()).toString());
    }

    @InterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface ShouldLog {}

    @ShouldLog
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION)
    public static class LoggingInterceptor
    {
        private static final AtomicInteger calls = new AtomicInteger();

        @AroundInvoke
        Object logCall(InvocationContext context) throws Exception
        {
            calls.incrementAndGet();
            return context.proceed();
        }
    }

    @ApplicationScoped
    public static class EchoService
    {
        @ShouldLog
        @CasualService(name = "logged-echo", category = "test")
        public InboundResponse logged(InboundRequest request)
        {
            return InboundResponse.createBuilder().buffer(request.getBuffer()).build();
        }

        @CasualService(name = "plain-echo", category = "test")
        public InboundResponse plain(InboundRequest request)
        {
            return InboundResponse.createBuilder().buffer(request.getBuffer()).build();
        }
    }
}
