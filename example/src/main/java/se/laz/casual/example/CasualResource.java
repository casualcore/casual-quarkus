/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import se.laz.casual.api.CasualRuntimeException;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.CasualBufferType;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.buffer.type.CStringBuffer;
import se.laz.casual.api.buffer.type.JsonBuffer;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.buffer.type.ServiceBuffer;
import se.laz.casual.api.buffer.type.fielded.FieldedTypeBuffer;
import se.laz.casual.api.buffer.type.fielded.FieldedTypeBufferEncoder;
import se.laz.casual.api.buffer.type.fielded.marshalling.FieldedTypeBufferProcessor;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.flags.ServiceReturnState;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.CasualConnectionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
            @DefaultValue("X_OCTET/")
            @QueryParam("bufferType") String bufferType,
            InputStream inputStream)
    {
        try
        {
            byte[] data = IOUtils.toByteArray(inputStream);
            Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
            CasualBuffer buffer = createBuffer(data, CasualBufferType.unmarshall(bufferType));
            return Uni.createFrom().completionStage(
                              () -> makeServiceCallAsync(serviceName, buffer, flags)
                      )
                      .map(value -> Response.ok().entity(value.getBytes().get(0)).build())
                      .onFailure().recoverWithItem(this::buildErrorResponse);
        }
        catch (IOException e)
        {
            return Uni.createFrom().failure(e);
        }
    }

    @GET
    @Path("simpleObject")
    public Uni<Response> simpleObject(
            @QueryParam("id") Long id,
            @QueryParam("name") String name)
    {
        Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
        SimpleObject simpleObject = new SimpleObject(id, name);
        CasualBuffer buffer = FieldedTypeBufferProcessor.marshall(simpleObject);
        return Uni.createFrom().completionStage(
                          () -> makeServiceCallAsync("casual/example/java/echoFielded", buffer, flags)
                  )
                  .map(value -> {
                      ServiceBuffer serviceBuffer = (ServiceBuffer) value;
                      CasualBufferType bufferType = CasualBufferType.unmarshall(serviceBuffer.getType());
                      if(bufferType != CasualBufferType.FIELDED)
                      {
                          throw new CasualRuntimeException("wrong buffer type: " + serviceBuffer.getType());
                      }
                      FieldedTypeBuffer fieldedTypeBuffer = FieldedTypeBuffer.create(serviceBuffer.getBytes());
                      SimpleObject returnValue = FieldedTypeBufferProcessor.unmarshall(fieldedTypeBuffer, SimpleObject.class);
                      return Response.ok().entity(returnValue).build();
                  })
                  .onFailure().recoverWithItem(this::buildErrorResponse);
    }

    private CasualBuffer createBuffer(byte[] data, CasualBufferType bufferType)
    {
        switch(bufferType)
        {
            case X_OCTET -> OctetBuffer.of(data);
            case JSON -> JsonBuffer.of(new String(data, StandardCharsets.UTF_8));
            case CSTRING -> CStringBuffer.of(new String(data, StandardCharsets.UTF_8));
            case FIELDED -> FieldedTypeBuffer.create(List.of(data));
            case JSON_JSCD -> throw new CasualRuntimeException("no JSON JSCD buffer type available, no bueno");
        }
        throw new CasualRuntimeException("unknown bufer type" + bufferType);
    }

    private CompletionStage<CasualBuffer> makeServiceCallAsync(String serviceName, CasualBuffer buffer, Flag<AtmiFlags> flags)
    {
        try
        {
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
        return casualTwo;
    }

}
