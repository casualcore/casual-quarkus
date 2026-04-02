/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.resource.ResourceException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.flags.ServiceReturnState;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.CasualConnectionFactory;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/casual")
public class CasualResource
{
    private AtomicInteger casualSwitch = new AtomicInteger(0);
    @Inject
    @Identifier("casual")
    private CasualConnectionFactory casualOne;

    @Inject
    @Identifier("casual-two")
    private CasualConnectionFactory casualTwo;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }

    @POST
    @Consumes("application/casual-x-octet")
    @Path("{serviceName}")
    public Uni<Response> serviceRequest(
            @PathParam("serviceName") String serviceName,
            InputStream inputStream) {

        return Uni.createFrom().completionStage(
                          () -> makeServiceCallAsync(inputStream, serviceName)
                  )
                  .map(buffer -> Response.ok().entity(buffer.getBytes().get(0)).build())
                  .onFailure().recoverWithItem(this::buildErrorResponse);
    }

    private CompletionStage<CasualBuffer> makeServiceCallAsync(InputStream inputStream, String serviceName)
    {
        try
        {
            byte[] data = IOUtils.toByteArray(inputStream);
            Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
            OctetBuffer buffer = OctetBuffer.of(data);
            try (CasualConnection connection = getConnectionFactory().getConnection())
            {
                return connection.tpacall(serviceName, buffer, flags)
                                 .thenApply(replyOpt -> {
                                     ServiceReturn<CasualBuffer> reply = replyOpt.orElseThrow(
                                             () -> new RuntimeException("No reply received from service " + serviceName)
                                     );

                                     if (reply.getServiceReturnState() == ServiceReturnState.TPSUCCESS)
                                     {
                                         return reply.getReplyBuffer();
                                     }
                                     else
                                     {
                                         throw new RuntimeException("tpcall failed: " + reply.getErrorState());
                                     }
                                 });
            }
        }
        catch (Exception e)
        {
            // Any synchronous error (e.g. reading InputStream) becomes a failed CompletionStage
            CompletableFuture<CasualBuffer> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    private Response buildErrorResponse(Throwable failure)
    {
        StringWriter sw = new StringWriter();
        failure.printStackTrace(new PrintWriter(sw));
        return Response.serverError()
                       .entity(sw.toString())
                       .build();
    }

    private CasualConnectionFactory getConnectionFactory()
    {
        return (casualSwitch.getAndIncrement() %2 == 0) ? casualOne : casualTwo;
    }

}
