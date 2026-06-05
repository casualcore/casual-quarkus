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
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import se.laz.casual.quarkus.CasualSPIDescriptor;
import se.laz.casual.quarkus.CasualSPIRecorder;
import se.laz.casual.quarkus.CasualSPIType;
import se.laz.casual.quarkus.CasualServiceDescriptor;
import se.laz.casual.quarkus.CasualServiceRecorder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

class CasualProcessor
{
    private static final String FEATURE = "casual";
    private static final String GROUP_NAME = "se.laz.casual";
    private static final DotName CASUAL_SERVICE = DotName.createSimple("se.laz.casual.api.service.CasualService");
    private static final DotName TRANSACTION_ANNOTATION = DotName.createSimple("jakarta.transaction.Transactional");
    private static final System.Logger LOG = System.getLogger(CasualProcessor.class.getName());

    @BuildStep
    FeatureBuildItem feature()
    {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexCasualDependencies(BuildProducer<IndexDependencyBuildItem> index)
    {
        // for jandex
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-api"));
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-handler-api"));
        // need to do this for each BufferHandler implementation that we want to support out of the box
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-handler-casual-service"));
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-inbound-handler-fielded-buffer"));
        index.produce(new IndexDependencyBuildItem(GROUP_NAME, "casual-json-provider-gson"));
        index.produce(new IndexDependencyBuildItem("com.google.code.gson", "gson"));
        // casual caller
        index.produce(new IndexDependencyBuildItem("se.laz.casual", "casual-caller"));
        index.produce(new IndexDependencyBuildItem("se.laz.casual", "casual-caller-http-client"));
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
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                "se.laz.casual.quarkus.QuarkusConnectionFactoryEntryValidationTimer"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                "se.laz.casual.quarkus.QuarkusTopologyChangedHandler"));
        // casual caller
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                "se.laz.casual.connection.caller.CasualCallerImpl"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                "se.laz.casual.connection.caller.TransactionLess"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                "se.laz.casual.http.HttpClient"));
    }

    @BuildStep
    void registerCasualCallerBeans(CombinedIndexBuildItem combinedIndex,
                                   BuildProducer<AdditionalBeanBuildItem> additionalBeans)
    {
        IndexView index = combinedIndex.getIndex();
        DotName inject = DotName.createSimple("jakarta.inject.Inject");

        // Register all casual-caller classes that have @Inject constructors
        for (AnnotationInstance ann : index.getAnnotations(inject))
        {
            if (ann.target().kind() == AnnotationTarget.Kind.METHOD
                    && ann.target().asMethod().isConstructor())
            {
                String className = ann.target().asMethod().declaringClass().name().toString();
                if (className.startsWith("se.laz.casual.connection.caller") || className.startsWith("se.laz.casual.http"))
                {
                    additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(className));
                }
            }
        }

        // Beans without @Inject that are still CDI-managed (no-arg constructor, no scope annotation)
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                "se.laz.casual.connection.caller.Lookup"));

        // needed for fat jar applications
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                                                       .addBeanClass("se.laz.casual.quarkus.CustomConnectionFactoryFinder")
                                                       .setUnremovable()
                                                       .build());

    }

    @BuildStep
    void registerSpiImplementationsForServiceLoader(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<ServiceProviderBuildItem> serviceProviders,
            BuildProducer<CasualSpiBuildItem> spiBuildItems,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans)
    {
        // These are the SPI types that a user application can implement
        // if not, default Casual JCA implementations are used
        List<DotName> spiInterfaces = List.of(
                DotName.createSimple("se.laz.casual.jca.inbound.handler.buffer.BufferHandler"),
                DotName.createSimple("se.laz.casual.jca.inbound.handler.service.ServiceHandler"),
                DotName.createSimple("se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension"),
                DotName.createSimple("se.laz.casual.api.buffer.type.fielded.marshalling.FieldedMarshaller")
        );
        IndexView index = combinedIndex.getIndex();
        for (DotName interfaceName : spiInterfaces)
        {
            String interfaceClassName = interfaceName.toString();
            for (ClassInfo implementation : index.getAllKnownImplementations(interfaceName))
            {
                String implClassName = implementation.name().toString();
                serviceProviders.produce(new ServiceProviderBuildItem(
                        interfaceClassName,
                        implClassName
                ));
                additionalBeans.produce(AdditionalBeanBuildItem.builder()
                                                               .addBeanClass(implClassName)
                                                               .setUnremovable()
                                                               .setDefaultScope(BuiltinScope.APPLICATION.getName())
                                                               .build());
                spiBuildItems.produce(new CasualSpiBuildItem(interfaceName.toString(), implClassName));
                LOG.log(System.Logger.Level.INFO, () -> "Registered SPI implementation for ServiceLoader + CDI: " + implClassName);
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
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD)
            {
                continue;
            }
            String className = annotation.target().asMethod().declaringClass().name().toString();
            String methodName = annotation.target().asMethod().name();
            String serviceName = annotation.value("name").asString();
            AnnotationValue categoryValue = annotation.value("category");
            String category = categoryValue != null ? categoryValue.asString() : "";

            String transactionType = null;
            AnnotationInstance txAnn = annotation.target().asMethod().annotation(TRANSACTION_ANNOTATION);
            if (txAnn != null)
            {
                AnnotationValue value = txAnn.value();
                //  defaults to REQUIRED as per:
                //  https://jakarta.ee/specifications/transactions/2.0/apidocs/jakarta/transaction/transactional
                transactionType = value.asEnum();
            }
            unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(className));
            MethodInfo method = annotation.target().asMethod();
            List<String> params = method.parameters().stream()
                                        .map(p -> p.type().name().toString())
                                        .toList();
            casualServices.produce(new CasualServiceBuildItem(serviceName, className, methodName, category, params, transactionType));
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
                    item.getParameterTypes(),
                    item.getTransactionType()));
        }
        recorder.registerServices(beanContainer.getValue(), Collections.unmodifiableList(descriptors));
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    void registerSPIImplementations(CasualSPIRecorder recorder,
                                    List<CasualSpiBuildItem> spiBuildItems)
    {
        List<CasualSPIDescriptor> descriptors = new ArrayList<>();
        for (var item : spiBuildItems)
        {
            descriptors.add(new CasualSPIDescriptor(
                    CasualSPIType.unmarshall(item.getServiceInterface()),
                    item.getImplementationClass()));
        }
        recorder.registerSpiImplementations(Collections.unmodifiableList(descriptors));
    }
}
