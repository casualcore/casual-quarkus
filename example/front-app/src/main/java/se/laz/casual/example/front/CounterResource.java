/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.front;

import jakarta.inject.Inject;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
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
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.connection.caller.CasualCaller;

import java.io.IOException;
import java.io.InputStream;

@Path("/casualcallersync")
public class CounterResource
{
    private final CasualCaller casualCaller;
    private final TransactionManager transactionManager;

    @Inject
    public CounterResource(CasualCaller casualCaller, TransactionManager transactionManager)
    {
        this.casualCaller = casualCaller;
        this.transactionManager = transactionManager;
    }

    @POST
    @Consumes("application/casual-x-octet")
    @Path("{serviceName}")
    @Transactional(Transactional.TxType.REQUIRED)
    public Response serviceRequest(
            @PathParam("serviceName") String serviceName,
            InputStream inputStream) throws IOException, SystemException
    {
        byte[] data = IOUtils.toByteArray(inputStream);
        Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
        CasualBuffer buffer = OctetBuffer.of(data);
        ServiceReturn<CasualBuffer> result = casualCaller.tpcall(serviceName, buffer, flags);
        if (result.getErrorState() == ErrorState.TPENOENT)
        {
            transactionManager.setRollbackOnly();
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
}
