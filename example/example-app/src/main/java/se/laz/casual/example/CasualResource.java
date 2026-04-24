/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static java.lang.System.Logger.Level.ERROR;

@Path("/casual")
public class CasualResource
{
    private static final System.Logger LOG = System.getLogger(CasualResource.class.getName());
    CasualConnectionFactory casualOne;

    @Inject
    public CasualResource(@Identifier("casual") CasualConnectionFactory casualOne)
    {
        this.casualOne = casualOne;
    }

    @POST
    @Consumes("application/casual-x-octet")
    @Path("{serviceName}")
    @Transactional(Transactional.TxType.REQUIRED)
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
                      // Important: this ensures Narayana's commit() doesn't happen on the Netty thread (which would deadlock)
                      .emitOn(Infrastructure.getDefaultWorkerPool())
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
    @Transactional(Transactional.TxType.REQUIRED)
    public Uni<Response> simpleObject(
            @QueryParam("id") Long id,
            @QueryParam("name") String name)
    {
        Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
        SimpleObject simpleObject = new SimpleObject(id, name);
        CasualBuffer buffer = FieldedTypeBufferProcessor.marshall(simpleObject);
        return Uni.createFrom().completionStage(
                          () -> makeServiceCallAsync("echoFielded", buffer, flags)
                  )
                  // Important: this ensures Narayana's commit() doesn't happen on the Netty thread (which would deadlock)
                  .emitOn(Infrastructure.getDefaultWorkerPool())
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
        return switch(bufferType)
        {
            case X_OCTET -> OctetBuffer.of(data);
            case JSON -> JsonBuffer.of(new String(data, StandardCharsets.UTF_8));
            case CSTRING -> CStringBuffer.of(new String(data, StandardCharsets.UTF_8));
            case FIELDED -> FieldedTypeBuffer.create(List.of(data));
            case JSON_JSCD -> throw new CasualRuntimeException("no JSON JSCD buffer type available");
        };
    }

    private CompletionStage<CasualBuffer> makeServiceCallAsync(String serviceName, CasualBuffer buffer, Flag<AtmiFlags> flags) {
        // We use supplyAsync to jump onto a Platform Thread immediately
        return CompletableFuture.supplyAsync(() -> {
                                    try {
                                        // This is the blocking/pinning-prone JCA call
                                        return casualOne.getConnection();
                                    } catch (Exception e) {
                                        // If the pool is exhausted or throws, we wrap it
                                        throw new CompletionException(e);
                                    }
                                }, Infrastructure.getDefaultExecutor())
                                .thenCompose(connection -> {
                                    // Now we are back in the async world with a valid connection
                                    return connection.tpacall(serviceName, buffer, flags)
                                                     .handle((reply, err) -> {
                                                         // ALWAYS close the connection handle, even on failure
                                                         try {
                                                             connection.close();
                                                         } catch (Exception e) {
                                                             LOG.log(ERROR, () -> "Failed to close JCA connection", e);
                                                         }
                                                         if (err != null) {
                                                             throw (err instanceof CompletionException) ?
                                                                     (CompletionException)err : new CompletionException(err);
                                                         }
                                                         return reply.orElseThrow(() -> new CasualRuntimeException("No reply"))
                                                                     .getReplyBuffer();
                                                     });
                                });
    }


    private Response buildErrorResponse(Throwable failure)
    {
        StringWriter sw = new StringWriter();
        failure.printStackTrace(new PrintWriter(sw));
        return Response.serverError()
                       .entity(sw.toString())
                       .build();
    }

}
