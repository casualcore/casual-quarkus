/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import se.laz.casual.quarkus.CasualServiceDescriptor;
import se.laz.casual.quarkus.CasualServiceRecorder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

class CasualProcessor
{
    private static final String FEATURE = "casual";
    private static final String GROUP_NAME = "se.laz.casual";
    private static final DotName CASUAL_SERVICE = DotName.createSimple("se.laz.casual.api.service.CasualService");

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
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-handler-casual-service"));
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-json-provider-gson"));
        index.produce(new IndexDependencyBuildItem("com.google.code.gson", "gson"));
    }

    @BuildStep
    void registerRuntimeBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans)
    {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualQuarkusResourceAdapterFactory"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualQuarkusServiceRegistry"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualMessageEndpoint"));
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
            casualServices.produce(new CasualServiceBuildItem(serviceName, className, methodName, category));
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
                    item.getCategory()));
        }
        recorder.registerServices(beanContainer.getValue(), descriptors);
    }
}
