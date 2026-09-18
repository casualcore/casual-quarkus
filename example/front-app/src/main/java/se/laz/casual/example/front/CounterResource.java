/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.front;

import jakarta.inject.Inject;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import se.laz.casual.api.CasualRuntimeException;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.flags.ErrorState;

import java.io.IOException;
import java.io.InputStream;

@Path("/casualcallersync")
public class CounterResource
{
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 50;
    private final TransactionalCounterCall counterCall;

    @Inject
    public CounterResource(TransactionalCounterCall counterCall)
    {
        this.counterCall = counterCall;
    }

    @POST
    @Consumes("application/casual-x-octet")
    @Path("{serviceName}")
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public Response serviceRequest(
            @PathParam("serviceName") String serviceName,
            InputStream inputStream) throws IOException, SystemException
    {
        byte[] data = IOUtils.toByteArray(inputStream);
        CasualBuffer buffer = OctetBuffer.of(data);
        try
        {
            ServiceReturn<CasualBuffer> result = counterCall.call(serviceName, buffer);
            for (int attempt = 1; attempt < MAX_ATTEMPTS && result.getErrorState() == ErrorState.TPESVCFAIL; attempt++)
            {
                // During shutdown, the node can receive requests before the front receives domain disconnect.
                // The draining node rejects its calls towards the database locally with TPENOENT, which
                // CounterForwarder reports to the front as TPESVCFAIL. This shutdown race is expected.
                // Wait after rollback so disconnect can arrive before the next transaction.
                Thread.sleep(RETRY_DELAY_MILLIS);
                result = counterCall.call(serviceName, buffer);
            }
            if (result.getErrorState() == ErrorState.TPENOENT)
            {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                               .entity(result.getErrorState().name())
                               .build();
            }
            if (result.getErrorState() != ErrorState.OK)
            {
                throw new CasualRuntimeException("Error: " + result.getErrorState().name());
            }
            return Response.ok().entity(result.getReplyBuffer().getBytes().get(0)).build();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                           .entity("Retry interrupted")
                           .build();
        }
        catch (CasualRuntimeException e)
        {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                           .entity(e.getMessage())
                           .build();
        }
    }
}
