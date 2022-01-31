/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandInterceptor;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.connectivity.service.messaging.DefaultClientActorPropsFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoEventSourceITAssertions;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Test;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;

/**
 * Tests {@link ConnectionPersistenceOperationsActor}.
 */
@AllValuesAreNonnullByDefault
public final class ConnectionPersistenceOperationsActorIT extends MongoEventSourceITAssertions<ConnectionId> {

    @Test
    public void purgeEntitiesWithoutNamespace() {
        assertPurgeEntitiesWithoutNamespace();
    }

    @Override
    protected String getServiceName() {
        // this loads the connectivity.conf from module "ditto-connectivity-service" as ActorSystem conf
        return "connectivity";
    }

    @Override
    protected String getResourceType() {
        return ConnectivityCommand.RESOURCE_TYPE;
    }

    @Override
    protected ConnectionId toEntityId(final CharSequence entityId) {
        return ConnectionId.of(entityId);
    }

    @Override
    protected Object getCreateEntityCommand(final ConnectionId id) {
        final AuthorizationContext authorizationContext =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("subject"));
        final Source source =
                ConnectivityModelFactory.newSource(authorizationContext, "address");
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(id, ConnectionType.AMQP_091, ConnectivityStatus.CLOSED,
                        "amqp://user:pass@8.8.8.8:5671")
                        .sources(Collections.singletonList(source))
                        .build();
        return CreateConnection.of(connection, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreateConnectionResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final ConnectionId id) {
        return RetrieveConnection.of(id, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getRetrieveEntityResponseClass() {
        return RetrieveConnectionResponse.class;
    }

    @Override
    protected Class<?> getEntityNotAccessibleClass() {
        return ConnectionNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {

        final Props opsActorProps = ConnectionPersistenceOperationsActor.props(pubSubMediator, mongoDbConfig, config,
                persistenceOperationsConfig);
        return actorSystem.actorOf(opsActorProps, ConnectionPersistenceOperationsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator,
            final ConnectionId id) {

        // essentially never restart
        final TestProbe proxyActorProbe = new TestProbe(system, "proxyActor");
        final ConnectivityCommandInterceptor dummyInterceptor = (command, connectionSupplier) -> {};
        final ConnectionPriorityProviderFactory dummyPriorityProvider = (connectionPersistenceActor, log) ->
                (connectionId, correlationId) -> CompletableFuture.completedFuture(4711);
        final ClientActorPropsFactory entityActorFactory = DefaultClientActorPropsFactory.getInstance();
        final Props props =
                ConnectionSupervisorActor.props(proxyActorProbe.ref(), entityActorFactory,
                        dummyInterceptor, dummyPriorityProvider, pubSubMediator);

        return system.actorOf(props, String.valueOf(id));
    }

}
