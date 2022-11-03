/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.grpc.discovery;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Status;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.grpc.channels.GrpcDefaultManagedChannelConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.micronaut.grpc.discovery.GrpcNameResolverProvider.ENABLED;


/**
 * Implementation of {@link NameResolverProvider} that allows using Micronaut's {@link DiscoveryClient}
 * to perform service discovery.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = DiscoveryClient.class)
@Requires(property = ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class GrpcNameResolverProvider extends NameResolverProvider implements LifeCycle<GrpcNameResolverProvider> {
    public static final String ENABLED = GrpcDefaultManagedChannelConfiguration.PREFIX + ".discovery.enabled";
    public static final int PRIORITY = 7;

    private static final String SCHEME = "svc";
    private final DiscoveryClient discoveryClient;
    private final List<ServiceInstanceList> serviceInstanceLists;
    private boolean operational = false;

    /**
     * Default constructor.
     * @param discoveryClient The discovery client
     * @param serviceInstanceLists The server instance list
     */
    protected GrpcNameResolverProvider(
            DiscoveryClient discoveryClient,
            List<ServiceInstanceList> serviceInstanceLists) {
        this.discoveryClient = discoveryClient;
        this.serviceInstanceLists = serviceInstanceLists;
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return PRIORITY;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        final String serviceId = targetUri.toString();
        final String resolvedServiceId = serviceId.startsWith("svc:///") ? serviceId.substring(7) : serviceId;
        if (resolvedServiceId.contains(":")) {
            return new NameResolver() {
                @Override
                public void start(Listener listener) {
                    final String[] hostAndPort = resolvedServiceId.split(":");
                    final List<EquivalentAddressGroup> equivalentAddressGroups =
                            Collections.singletonList(new EquivalentAddressGroup(new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1]))));
                    listener.onAddresses(equivalentAddressGroups, Attributes.EMPTY);
                }

                @Override
                public String getServiceAuthority() {
                    return resolvedServiceId;
                }

                @Override
                public void shutdown() {

                }
            };
        }

        if (!NameUtils.isHyphenatedLowerCase(resolvedServiceId)) {
            throw new IllegalArgumentException("Invalid service ID [" + resolvedServiceId + "]. Service IDs should be kebab-case (lowercase / hyphen separated). For example 'greeting-service'.");

        }
        return new NameResolver() {
            private volatile Listener listener;
            private Disposable disposable;

            @Override
            public String getServiceAuthority() {
                return "//" + resolvedServiceId;
            }

            @Override
            public void refresh() {
                for (ServiceInstanceList serviceInstanceList : serviceInstanceLists) {
                    if (serviceInstanceList.getID().equals(resolvedServiceId)) {
                        listener.onAddresses(
                                toAddresses(serviceInstanceList.getInstances()),
                                Attributes.EMPTY
                        );
                        return;
                    }
                }

                this.disposable = Flux.from(discoveryClient.getInstances(resolvedServiceId)).subscribe(
                        (instances) -> {
                            if (CollectionUtils.isNotEmpty(instances)) {
                                final List<EquivalentAddressGroup> servers = toAddresses(instances);
                                listener.onAddresses(
                                        servers, Attributes.EMPTY
                                );
                            } else {
                                if (targetUri.getHost() != null && targetUri.getPort() > -1) {
                                    listener.onAddresses(
                                            Collections.singletonList(new EquivalentAddressGroup(
                                                    new InetSocketAddress(
                                                            targetUri.getHost(),
                                                            targetUri.getPort()
                                                    )
                                            )), Attributes.EMPTY
                                    );
                                } else {
                                    listener.onError(Status.UNAVAILABLE.withCause(
                                            new NoAvailableServiceException(serviceId)
                                    ));
                                }

                            }
                        },
                        (error) -> listener.onError(Status.fromThrowable(error))
                );
            }

            @Override
            public void start(Listener listener) {
                this.listener = listener;
                refresh();
            }

            private List<EquivalentAddressGroup> toAddresses(List<ServiceInstance> instances) {
                final List<SocketAddress> socketAddresses = instances.stream().map(serviceInstance ->
                        new InetSocketAddress(serviceInstance.getHost(), serviceInstance.getPort())
                ).collect(Collectors.toList());
                return Collections.singletonList(new EquivalentAddressGroup(socketAddresses));
            }

            @Override
            public void shutdown() {
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                }
            }
        };
    }

    @Override
    public String getDefaultScheme() {
        return SCHEME;
    }

    @Override
    public boolean isRunning() {
        return operational;
    }

    @NonNull
    @Override
    @PostConstruct
    public GrpcNameResolverProvider start() {
        NameResolverRegistry.getDefaultRegistry().register(this);
        operational = true;
        return this;
    }

    @NonNull
    @Override
    @PreDestroy
    public GrpcNameResolverProvider stop() {
        NameResolverRegistry.getDefaultRegistry().deregister(this);
        operational = false;
        return this;
    }

    /**
     * Ensures name resolver is registered.
     */
    @Singleton
    @Internal
    static final class ManagedChannelBuilderListener implements BeanCreatedEventListener<ManagedChannelBuilder<?>> {
        private BeanProvider<GrpcNameResolverProvider> beanProvider;

        ManagedChannelBuilderListener(@Nullable BeanProvider<GrpcNameResolverProvider> beanProvider) {
            this.beanProvider = beanProvider;
        }

        @Override
        public ManagedChannelBuilder<?> onCreated(BeanCreatedEvent<ManagedChannelBuilder<?>> event) {
            if (beanProvider != null) {
                // init
                beanProvider.get();
                beanProvider = null;
            }
            return event.getBean();
        }
    }
}
