/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.starter;

import static org.eclipse.ditto.policies.api.PoliciesMessagingConstants.CLUSTER_ROLE;

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.ClusterUtil;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.internal.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.PersistencePingActor;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.PersistenceCleanupActor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.PolicyAnnouncementPubSubFactory;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.service.common.config.PoliciesConfig;
import org.eclipse.ditto.policies.service.persistence.actors.PoliciesPersistenceStreamingActorCreator;
import org.eclipse.ditto.policies.service.persistence.actors.PolicyPersistenceOperationsActor;
import org.eclipse.ditto.policies.service.persistence.actors.PolicySupervisorActor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Parent Actor which takes care of supervision of all other Actors in our system.
 */
public final class PoliciesRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "policiesRoot";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;

    @SuppressWarnings("unused")
    private PoliciesRootActor(final PoliciesConfig policiesConfig,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {

        final var actorSystem = getContext().system();
        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(actorSystem).withRole(CLUSTER_ROLE);

        final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub =
                PolicyAnnouncementPubSubFactory.of(getContext(), actorSystem).startDistributedPub();

        final var policySupervisorProps =
                PolicySupervisorActor.props(pubSubMediator, snapshotAdapter, policyAnnouncementPub);

        final ActorRef persistenceStreamingActor = startChildActor(PoliciesPersistenceStreamingActorCreator.ACTOR_NAME,
                PoliciesPersistenceStreamingActorCreator.props());

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());
        pubSubMediator.tell(DistPubSubAccess.put(persistenceStreamingActor), getSelf());

        final var clusterConfig = policiesConfig.getClusterConfig();
        final ActorRef policiesShardRegion = ClusterSharding.get(actorSystem)
                .start(PoliciesMessagingConstants.SHARD_REGION, policySupervisorProps, shardingSettings,
                        ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem));

        final var mongoReadJournal = MongoReadJournal.newInstance(actorSystem);
        startClusterSingletonActor(
                PersistencePingActor.props(policiesShardRegion, policiesConfig.getPingConfig(), mongoReadJournal),
                PersistencePingActor.ACTOR_NAME);

        startChildActor(PolicyPersistenceOperationsActor.ACTOR_NAME,
                PolicyPersistenceOperationsActor.props(pubSubMediator, policiesConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), policiesConfig.getPersistenceOperationsConfig()));

        retrieveStatisticsDetailsResponseSupplier = RetrieveStatisticsDetailsResponseSupplier.of(policiesShardRegion,
                PoliciesMessagingConstants.SHARD_REGION, log);

        final var cleanupConfig = policiesConfig.getPolicyConfig().getCleanupConfig();
        final var cleanupActorProps = PersistenceCleanupActor.props(cleanupConfig, mongoReadJournal, CLUSTER_ROLE);
        startChildActor(PersistenceCleanupActor.NAME, cleanupActorProps);

        final var healthCheckConfig = policiesConfig.getHealthCheckConfig();
        final var hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        if (healthCheckConfig.getPersistenceConfig().isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final var healthCheckingActorOptions = hcBuilder.build();
        final var healthCheckingActorProps =
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, MongoHealthChecker.props());
        final ActorRef healthCheckingActor =
                startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME, healthCheckingActorProps);
        bindHttpStatusRoute(policiesConfig.getHttpConfig(), healthCheckingActor);
    }

    /**
     * Creates Akka configuration object Props for this PoliciesRootActor.
     *
     * @param policiesConfig the configuration reader of this service.
     * @param snapshotAdapter serializer and deserializer of the Policies snapshot store.
     * @param pubSubMediator the PubSub mediator Actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final PoliciesConfig policiesConfig,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {

        return Props.create(PoliciesRootActor.class, policiesConfig, snapshotAdapter, pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .build().orElse(super.createReceive());
    }

    private void startClusterSingletonActor(final Props props, final String name) {
        ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, name, props);
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the policy shard as requested ...");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

}
