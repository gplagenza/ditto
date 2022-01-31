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
package org.eclipse.ditto.policies.service.persistence.actors;

import java.util.Set;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractShardedPersistenceActor;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.PolicyCommandStrategies;
import org.eclipse.ditto.policies.service.persistence.actors.strategies.events.PolicyEventStrategies;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.persistence.RecoveryCompleted;

/**
 * PersistentActor which "knows" the state of a single {@link Policy}.
 */
public final class PolicyPersistenceActor
        extends AbstractShardedPersistenceActor<Command<?>, Policy, PolicyId, PolicyId, PolicyEvent<?>> {

    /**
     * The prefix of the persistenceId for Policies.
     */
    public static final String PERSISTENCE_ID_PREFIX = "policy:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-policies-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-policies-snapshots";

    private final ActorRef pubSubMediator;
    private final PolicyConfig policyConfig;
    private final ActorRef announcementManager;

    @SuppressWarnings("unused")
    private PolicyPersistenceActor(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager,
            final PolicyConfig policyConfig) {
        super(policyId, snapshotAdapter);
        this.pubSubMediator = pubSubMediator;
        this.announcementManager = announcementManager;
        this.policyConfig = policyConfig;
    }

    @SuppressWarnings("unused")
    private PolicyPersistenceActor(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager) {
        // not possible to call other constructor because "getContext()" is not available as argument of "this()"
        super(policyId, snapshotAdapter);
        this.pubSubMediator = pubSubMediator;
        this.announcementManager = announcementManager;
        final DittoPoliciesConfig policiesConfig = DittoPoliciesConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        this.policyConfig = policiesConfig.getPolicyConfig();
    }

    /**
     * Creates Akka configuration object {@link Props} for this PolicyPersistenceActor.
     *
     * @param policyId the ID of the Policy this Actor manages.
     * @param snapshotAdapter the adapter to serialize Policy snapshots.
     * @param pubSubMediator the PubSub mediator actor.
     * @param announcementManager manager of policy announcements.
     * @param policyConfig the policy config.
     * @return the Akka configuration Props object
     */
    public static Props props(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager,
            final PolicyConfig policyConfig) {

        return Props.create(PolicyPersistenceActor.class, policyId, snapshotAdapter, pubSubMediator,
                announcementManager, policyConfig);
    }

    static Props propsForTests(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager) {

        return Props.create(PolicyPersistenceActor.class, policyId, snapshotAdapter, pubSubMediator,
                announcementManager);
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + entityId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    protected Class<?> getEventClass() {
        return PolicyEvent.class;
    }

    @Override
    protected CommandStrategy.Context<PolicyId> getStrategyContext() {
        return DefaultContext.getInstance(entityId, log);
    }

    @Override
    protected PolicyCommandStrategies getCreatedStrategy() {
        return PolicyCommandStrategies.getInstance(policyConfig, getContext().getSystem());
    }

    @Override
    protected CommandStrategy<? extends Command<?>, Policy, PolicyId, PolicyEvent<?>> getDeletedStrategy() {
        return PolicyCommandStrategies.getCreatePolicyStrategy(policyConfig);
    }

    @Override
    protected EventStrategy<PolicyEvent<?>, Policy> getEventStrategy() {
        return PolicyEventStrategies.getInstance();
    }

    @Override
    protected ActivityCheckConfig getActivityCheckConfig() {
        return policyConfig.getActivityCheckConfig();
    }

    @Override
    protected SnapshotConfig getSnapshotConfig() {
        return policyConfig.getSnapshotConfig();
    }

    @Override
    protected boolean entityExistsAsDeleted() {
        return null != entity && entity.hasLifecycle(PolicyLifecycle.DELETED);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> newNotAccessibleExceptionBuilder() {
        return PolicyNotAccessibleException.newBuilder(entityId);
    }

    @Override
    protected void publishEvent(final PolicyEvent<?> event) {
        pubSubMediator.tell(DistPubSubAccess.publishViaGroup(PolicyEvent.TYPE_PREFIX, event), getSender());

        final boolean policyEnforcerInvalidatedPreemptively = Boolean.parseBoolean(event.getDittoHeaders()
                .getOrDefault(DittoHeaderDefinition.POLICY_ENFORCER_INVALIDATED_PREEMPTIVELY.getKey(),
                        Boolean.FALSE.toString()));
        if (!policyEnforcerInvalidatedPreemptively) {
            final PolicyTag policyTag = PolicyTag.of(entityId, event.getRevision());
            pubSubMediator.tell(DistPubSubAccess.publish(PolicyTag.PUB_SUB_TOPIC_INVALIDATE_ENFORCERS, policyTag),
                    getSender());
        }
    }

    @Override
    public void onMutation(final Command<?> command, final PolicyEvent<?> event, final WithDittoHeaders response,
            final boolean becomeCreated, final boolean becomeDeleted) {

        persistAndApplyEvent(event, (persistedEvent, resultingEntity) -> {
            if (shouldSendResponse(command.getDittoHeaders())) {
                notifySender(getSender(), response);
            }
            if (becomeDeleted) {
                becomeDeletedHandler();
            }
            if (becomeCreated) {
                becomeCreatedHandler();
            }
        });
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final Policy entity) {
        return entity.getImplementedSchemaVersion();
    }

    @Override
    protected boolean shouldSendResponse(final DittoHeaders dittoHeaders) {
        return dittoHeaders.isResponseRequired();
    }

    @Override
    protected boolean isEntityAlwaysAlive() {
        return isAlwaysAlive(entity);
    }

    @Override
    protected void recoveryCompleted(final RecoveryCompleted event) {
        if (entity != null) {
            announcementManager.tell(entity, ActorRef.noSender());
        }
        super.recoveryCompleted(event);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected PolicyEvent<?> modifyEventBeforePersist(final PolicyEvent<?> event) {
        final PolicyEvent<?> superEvent = super.modifyEventBeforePersist(event);

        if (willEntityBeAlwaysAlive(event)) {
            final DittoHeaders headersWithJournalTags = superEvent.getDittoHeaders()
                    .toBuilder()
                    .journalTags(Set.of(JOURNAL_TAG_ALWAYS_ALIVE))
                    .build();
            return superEvent.setDittoHeaders(headersWithJournalTags);
        }

        return superEvent;
    }

    @Override
    protected void onEntityModified() {
        if (entity != null) {
            announcementManager.tell(entity, ActorRef.noSender());
        }
    }

    private boolean willEntityBeAlwaysAlive(final PolicyEvent<?> policyEvent) {
        return isAlwaysAlive(getEventStrategy().handle(policyEvent, entity, getRevisionNumber()));
    }

    private boolean isAlwaysAlive(@Nullable final Policy policy) {
        if (policy == null) {
            return false;
        } else {
            return StreamSupport.stream(policy.spliterator(), false)
                    .map(PolicyEntry::getSubjects)
                    .flatMap(Subjects::stream)
                    .anyMatch(subject -> subject.getExpiry().isPresent());
        }
    }

}
