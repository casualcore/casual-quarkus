package se.laz.casual.example.json;

import com.google.gson.JsonElement;
import se.laz.casual.api.CasualRuntimeException;
import se.laz.casual.api.buffer.CasualBufferType;
import se.laz.casual.api.buffer.type.JsonBuffer;
import se.laz.casual.api.external.json.JsonProviderFactory;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandler;
import se.laz.casual.jca.inbound.handler.buffer.InboundRequestInfo;
import se.laz.casual.jca.inbound.handler.buffer.ServiceCallInfo;
import se.laz.casual.spi.Priority;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * A very simple JsonBufferHandler for use with the sum service
 */
public class JsonBufferHandler implements BufferHandler
{
    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_3;
    }
    @Override
    public boolean canHandleBuffer(String bufferType)
    {
        return CasualBufferType.JSON.getName().equalsIgnoreCase(bufferType);
    }

    @Override
    public ServiceCallInfo fromRequest(InboundRequestInfo requestInfo, InboundRequest request)
    {
        Method method = requestInfo.getRealMethod().orElseThrow(() -> new CasualRuntimeException("real method is missing"));
        JsonBuffer buffer = JsonBuffer.of(request.getBuffer().getBytes());
        String json = buffer.toString();
        var jsonProvider = JsonProviderFactory.getJsonProvider();  // cache it
        CasualJsonRequest envelope = jsonProvider.fromJson(json, CasualJsonRequest.class);
        if (envelope.params() == null)
        {
            return ServiceCallInfo.of(method, new Object[method.getParameterCount()]);
        }
        Object[] actualArgs = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++)
        {
            Class<?> targetType = method.getParameterTypes()[i];
            JsonElement paramElement = envelope.params().get(i);
            String paramJson = jsonProvider.toJson(paramElement);
            actualArgs[i] = jsonProvider.fromJson(paramJson, targetType);
        }
        return ServiceCallInfo.of(method, actualArgs);
    }

    @Override
    public InboundResponse toResponse(ServiceCallInfo info, Object result)
    {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("result", result);
        return InboundResponse.createBuilder()
                              .buffer(JsonBuffer.of(JsonProviderFactory.getJsonProvider().toJson(wrapper)))
                              .build();
    }
}


