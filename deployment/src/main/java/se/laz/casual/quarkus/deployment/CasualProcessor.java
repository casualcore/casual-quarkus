/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import se.laz.casual.quarkus.CasualServiceDescriptor;
import se.laz.casual.quarkus.CasualServiceRecorder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

class CasualProcessor
{
    private static final String FEATURE = "casual";
    private static final String GROUP_NAME = "se.laz.casual";
    private static final DotName CASUAL_SERVICE = DotName.createSimple("se.laz.casual.api.service.CasualService");
    private static final System.Logger LOG = System.getLogger(CasualProcessor.class.getName());

    @BuildStep
    FeatureBuildItem feature()
    {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexCasualDependencies(BuildProducer<IndexDependencyBuildItem> index)
    {
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-api"));
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-handler-api"));
        // need to do this for each BufferHandler implementation that we want to support out of the box
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-handler-casual-service"));
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-handler-fielded-buffer"));
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-json-provider-gson"));
        index.produce(new IndexDependencyBuildItem("com.google.code.gson", "gson"));
    }

    @BuildStep
    void registerRuntimeBeans(CombinedIndexBuildItem index, BuildProducer<AdditionalBeanBuildItem> additionalBeans)
    {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualQuarkusResourceAdapterFactory"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualQuarkusServiceRegistry"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualMessageEndpoint"));
    }

    @BuildStep
    void registerSpiImplementations(CombinedIndexBuildItem index, BuildProducer<AdditionalBeanBuildItem> additionalBeans,
                                    BuildProducer<ReflectiveClassBuildItem> reflectiveClasses)
    {

        List<DotName> interfaces = List.of(
                DotName.createSimple("se.laz.casual.jca.inbound.handler.buffer.BufferHandler"),
                DotName.createSimple("se.laz.casual.jca.inbound.handler.service.ServiceHandler"),
                DotName.createSimple("se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension")
        );

        for (DotName interfaceName : interfaces)
        {
            // find everything implementing the interface in the entire application
            // this means it also works for implementations in the user application
            for (ClassInfo implementation : index.getIndex().getAllKnownImplementors(interfaceName))
            {
                String className = implementation.name().toString();

                // make it a CDI bean and prevent pruning
                additionalBeans.produce(AdditionalBeanBuildItem.builder()
                                                               .addBeanClass(className)
                                                               .setUnremovable()
                                                               .setDefaultScope(BuiltinScope.APPLICATION.getName())
                                                               .build());

                // for SPI: Register for reflection so ServiceLoader works in Native Mode
                reflectiveClasses.produce(ReflectiveClassBuildItem.builder(className)
                                                                  .methods().fields().build());
                LOG.log(System.Logger.Level.INFO, () -> "Registered implementation for reflection and unremovable bean: " + implementation.name());
            }
        }
    }


    @BuildStep
    void discoverCasualServices(CombinedIndexBuildItem combinedIndex,
                                BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
                                BuildProducer<CasualServiceBuildItem> casualServices)
    {
        Collection<AnnotationInstance> annotations = combinedIndex.getIndex()
                                                                  .getAnnotations(CASUAL_SERVICE);
        for (AnnotationInstance annotation : annotations)
        {
            String className = annotation.target().asMethod().declaringClass().name().toString();
            String methodName = annotation.target().asMethod().name();
            String serviceName = annotation.value("name").asString();
            AnnotationValue categoryValue = annotation.value("category");
            String category = categoryValue != null ? categoryValue.asString() : "";
            unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(className));
            MethodInfo method = annotation.target().asMethod();
            List<String> params = method.parameters().stream()
                                        .map(p -> p.type().name().toString())
                                        .collect(Collectors.toList());
            casualServices.produce(new CasualServiceBuildItem(serviceName, className, methodName, category, params));
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    void registerCasualServices(CasualServiceRecorder recorder,
                                BeanContainerBuildItem beanContainer,
                                List<CasualServiceBuildItem> casualServices)
    {
        List<CasualServiceDescriptor> descriptors = new ArrayList<>();
        for (CasualServiceBuildItem item : casualServices)
        {
            descriptors.add(new CasualServiceDescriptor(
                    item.getServiceName(),
                    item.getClassName(),
                    item.getMethodName(),
                    item.getCategory(),
                    item.getParameterTypes()));
        }
        recorder.registerServices(beanContainer.getValue(), descriptors);
    }
}
