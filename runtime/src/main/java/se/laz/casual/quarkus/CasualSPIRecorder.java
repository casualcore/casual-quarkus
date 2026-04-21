package se.laz.casual.quarkus;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.annotations.Recorder;
import se.laz.casual.api.CasualRuntimeException;
import se.laz.casual.api.buffer.type.fielded.marshalling.FieldedMarshaller;
import se.laz.casual.api.buffer.type.fielded.marshalling.FieldedTypeBufferProcessor;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandler;
import se.laz.casual.jca.inbound.handler.buffer.BufferHandlerFactory;
import se.laz.casual.jca.inbound.handler.service.ServiceHandler;
import se.laz.casual.jca.inbound.handler.service.ServiceHandlerFactory;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension;
import se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtensionFactory;

import java.lang.System.Logger;
import java.util.List;

/**
 * Used to register, at runtime, the SPI implementations found at build time
 * This to make sure that user SPI implementations work also when running with quarkusDev
 */
@Recorder
public class CasualSPIRecorder
{
    private static final Logger LOG = System.getLogger(CasualSPIRecorder.class.getName());
    public void registerSpiImplementations(List<CasualSPIDescriptor> spiItems)
    {
        for (CasualSPIDescriptor item : spiItems)
        {
            try
            {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(item.implementationClass());
                InstanceHandle<?> instance = Arc.container().instance(clazz);
                Object impl = instance.isAvailable()
                        ? instance.get()
                        : clazz.getDeclaredConstructor().newInstance();
                switch(item.spiType())
                {
                    case BUFFER_HANDLER:
                        BufferHandlerFactory.register((BufferHandler) impl);
                        break;
                    case SERVICE_HANDLER:
                        ServiceHandlerFactory.register((ServiceHandler) impl);
                        break;
                    case SERVICE_HANDLER_EXTENSION:
                        ServiceHandlerExtensionFactory.register((ServiceHandlerExtension) impl);
                        break;
                    case FIELDED_MARSHALLER:
                        FieldedTypeBufferProcessor.register((FieldedMarshaller) impl);
                        break;
                }
                LOG.log(Logger.Level.INFO, () -> "Runtime registered SPI: " + item.implementationClass());
            }
            catch (Throwable e)
            {
                throw new CasualRuntimeException("Failed to register SPI " + item.implementationClass(), e);
            }
        }
    }
}
