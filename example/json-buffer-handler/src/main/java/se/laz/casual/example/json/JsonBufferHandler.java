package se.laz.casual.example.json;

import jakarta.enterprise.context.ApplicationScoped;
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

import java.lang.System.Logger;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.Logger.Level.INFO;

@ApplicationScoped
public class JsonBufferHandler implements BufferHandler
{
    private static final Logger LOG = System.getLogger(JsonBufferHandler.class.getName());
    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_3;
    }
    @Override
    public boolean canHandleBuffer(String bufferType)
    {
        LOG.log(INFO, () -> "JsonBufferHandler::canHandleBuffer " + bufferType);
        return CasualBufferType.JSON.getName().equalsIgnoreCase(bufferType);
    }

    @Override
    public ServiceCallInfo fromRequest(InboundRequestInfo requestInfo, InboundRequest request)
    {
        Method method = requestInfo.getRealMethod().orElseThrow(() -> new CasualRuntimeException("real method is missing"));
        String json = request.getBuffer().toString();
        if (json.startsWith("\"") && json.endsWith("\""))
        {
            // Basic unescaping for GSON double-encoding
            json = json.substring(1, json.length() - 1).replace("\\\"", "\"");
        }
        CasualJsonRequest envelope = JsonProviderFactory.getJsonProvider().fromJson(json, CasualJsonRequest.class);
        if (envelope.params() == null)
        {
            return ServiceCallInfo.of(method, new Object[method.getParameterCount()]);
        }
        Object[] actualArgs = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++)
        {
            // Since envelope.params() is Object[], GSON will have unmarshalled
            // nested objects into LinkedHashMaps or Doubles.
            // We re-marshal and unmarshal to the specific target type:
            String paramJson = JsonProviderFactory.getJsonProvider().toJson(envelope.params()[i]);
            actualArgs[i] = JsonProviderFactory.getJsonProvider().fromJson(paramJson, method.getParameterTypes()[i]);
        }
        return ServiceCallInfo.of(method, actualArgs);
    }


    @Override
    public InboundResponse toResponse(ServiceCallInfo info, Object result) {
        // You can also wrap the response: {"result": 15, "error": null}
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("result", result);
        return InboundResponse.createBuilder()
                              .buffer(JsonBuffer.of(JsonProviderFactory.getJsonProvider().toJson(wrapper)))
                              .build();
    }
}


