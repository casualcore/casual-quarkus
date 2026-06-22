/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
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
import se.laz.casual.api.buffer.type.fielded.FieldedTypeBuffer;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.connection.caller.CasualCaller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Path("/casualcallersync")
public class CasualResourceCallerSync
{
    private final CasualCaller casualCaller;

    @Inject
    public CasualResourceCallerSync(CasualCaller casualCaller)
    {
        this.casualCaller = casualCaller;
    }

    @POST
    @Consumes("application/casual-x-octet")
    @Path("{serviceName}")
    @Transactional(Transactional.TxType.REQUIRED)
    public Response serviceRequest(
            @PathParam("serviceName") String serviceName,
            @DefaultValue("X_OCTET/")
            @QueryParam("bufferType") String bufferType,
            @DefaultValue("1")
            @QueryParam("numberOfCalls") Integer numberOfCalls,
            InputStream inputStream) throws IOException
    {
        if (numberOfCalls < 1)
        {
            return Response.serverError().entity("Invalid number of calls, expecting 1-n").build();
        }
        byte[] data = IOUtils.toByteArray(inputStream);
        Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
        CasualBuffer buffer = createBuffer(data, CasualBufferType.unmarshall(bufferType));
        ServiceReturn<CasualBuffer> result = null;
        while (numberOfCalls-- > 0)
        {
            result = casualCaller.tpcall(serviceName, buffer, flags);
        }
        if (result.getErrorState() != ErrorState.OK)
        {
            throw new CasualRuntimeException("Error: " + result.getErrorState());
        }
        return Response.ok().entity(result.getReplyBuffer().getBytes().get(0)).build();
    }

    private CasualBuffer createBuffer(byte[] data, CasualBufferType bufferType)
    {
        return switch (bufferType)
        {
            case X_OCTET -> OctetBuffer.of(data);
            case JSON -> JsonBuffer.of(new String(data, StandardCharsets.UTF_8));
            case CSTRING -> CStringBuffer.of(new String(data, StandardCharsets.UTF_8));
            case FIELDED -> FieldedTypeBuffer.create(List.of(data));
            case JSON_JSCD -> throw new CasualRuntimeException("no JSON JSCD buffer type available");
        };
    }
}
