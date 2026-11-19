/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;
import se.laz.casual.api.buffer.CasualHeaders;
import se.laz.casual.api.buffer.type.CStringBuffer;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.connection.caller.CasualCaller;
import se.laz.casual.example.user.handlers.CallMetadata;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(
        value = SelfConnectedCasualTestResource.class,
        restrictToAnnotatedClass = true)
class CallMetadataEndToEndTest
{
    private static final String SERVICE = "interceptor-metadata";
    // The example ServiceHandlerExtension reads "breadcrumb". Only the CDI interceptor reads this header.
    private static final String HEADER = "interceptor-breadcrumb";

    @Inject
    CasualCaller caller;

    @Test
    void breadcrumbHeaderIsAvailableToInboundService()
    {
        UUID breadcrumb = UUID.randomUUID();

        given()
                .header(CallMetadata.BREADCRUMB_HEADER, breadcrumb.toString())
                .when()
                .get("/casualcallersync/call-metadata")
                .then()
                .statusCode(200)
                .body(equalTo(breadcrumb.toString()));
    }

    @Test
    void serviceReadsOnlyTheMetadataPopulatedByItsInterceptorForThisCall()
    {
        UUID breadcrumb = UUID.randomUUID();
        CasualHeaders headers = CasualHeaders.newBuilder().add(HEADER, breadcrumb.toString()).build();
        assertEquals(breadcrumb.toString(), call(headers));
        assertEquals("", call(CasualHeaders.newBuilder().build()));
    }

    private String call(CasualHeaders headers)
    {
        var result = caller.tpcall(SERVICE, CStringBuffer.of("", headers), Flag.of(AtmiFlags.NOFLAG));
        assertEquals(ErrorState.OK, result.getErrorState());
        return CStringBuffer.of(result.getReplyBuffer().getBytes()).toString();
    }

    @InterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface PopulateCallMetadata {}

    @PopulateCallMetadata
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION)
    public static class CallMetadataInterceptor
    {
        @Inject
        CallMetadata metadata;

        @AroundInvoke
        Object populate(InvocationContext context) throws Exception
        {
            InboundRequest request = (InboundRequest) context.getParameters()[0];
            var values = request.getBuffer().getHeaders().get(HEADER);
            if (!values.isEmpty())
            {
                metadata.setBreadcrumb(UUID.fromString(values.getFirst()));
            }
            return context.proceed();
        }
    }

    @ApplicationScoped
    public static class MetadataService
    {
        @Inject
        CallMetadata metadata;

        @PopulateCallMetadata
        @CasualService(name = SERVICE, category = "test")
        public InboundResponse read(InboundRequest request)
        {
            String breadcrumb = metadata.getBreadcrumb().map(UUID::toString).orElse("");
            return InboundResponse.createBuilder().buffer(CStringBuffer.of(breadcrumb)).build();
        }
    }
}
