/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

/**
 * Default implementation of {@link DittoProtocolSub}.
 */
final class DittoProtocolSubImpl implements DittoProtocolSub {

    private final DistributedSub liveSignalSub;
    private final DistributedSub twinEventSub;
    private final DistributedSub policyAnnouncementSub;
    private final DistributedAcks distributedAcks;

    private DittoProtocolSubImpl(final DistributedSub liveSignalSub,
            final DistributedSub twinEventSub,
            final DistributedSub policyAnnouncementSub,
            final DistributedAcks distributedAcks) {
        this.liveSignalSub = liveSignalSub;
        this.twinEventSub = twinEventSub;
        this.policyAnnouncementSub = policyAnnouncementSub;
        this.distributedAcks = distributedAcks;
    }

    static DittoProtocolSubImpl of(final ActorSystem system, final DistributedAcks distributedAcks) {
        final DistributedSub liveSignalSub =
                LiveSignalPubSubFactory.of(system, distributedAcks).startDistributedSub();
        final DistributedSub twinEventSub =
                ThingEventPubSubFactory.readSubjectsOnly(system, distributedAcks).startDistributedSub();
        final DistributedSub policyAnnouncementSub =
                PolicyAnnouncementPubSubFactory.of(system, system).startDistributedSub();
        return new DittoProtocolSubImpl(liveSignalSub, twinEventSub, policyAnnouncementSub, distributedAcks);
    }

    @Override
    public CompletionStage<Void> subscribe(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber,
            @Nullable final String group) {
        final CompletionStage<?> nop = CompletableFuture.completedFuture(null);
        return partitionByStreamingTypes(types,
                liveTypes -> !liveTypes.isEmpty()
                        ? liveSignalSub.subscribeWithFilterAndGroup(topics, subscriber, toFilter(liveTypes), group)
                        : nop,
                hasTwinEvents -> hasTwinEvents
                        ? twinEventSub.subscribeWithFilterAndGroup(topics, subscriber, null, group)
                        : nop,
                hasPolicyAnnouncements -> hasPolicyAnnouncements
                        ? policyAnnouncementSub.subscribeWithFilterAndGroup(topics, subscriber, null, group)
                        : nop
        );
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        liveSignalSub.removeSubscriber(subscriber);
        twinEventSub.removeSubscriber(subscriber);
        policyAnnouncementSub.removeSubscriber(subscriber);
        distributedAcks.removeSubscriber(subscriber);
    }

    @Override
    public CompletionStage<Void> updateLiveSubscriptions(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber) {

        return partitionByStreamingTypes(types,
                liveTypes -> !liveTypes.isEmpty()
                        ? liveSignalSub.subscribeWithFilterAndGroup(topics, subscriber, toFilter(liveTypes), null)
                        : liveSignalSub.unsubscribeWithAck(topics, subscriber),
                hasTwinEvents -> CompletableFuture.completedStage(null),
                hasPolicyAnnouncements -> CompletableFuture.completedStage(null)
        );
    }

    @Override
    public CompletionStage<Void> removeTwinSubscriber(final ActorRef subscriber, final Collection<String> topics) {
        return twinEventSub.unsubscribeWithAck(topics, subscriber).thenApply(ack -> null);
    }

    @Override
    public CompletionStage<Void> removePolicyAnnouncementSubscriber(final ActorRef subscriber,
            final Collection<String> topics) {
        return policyAnnouncementSub.unsubscribeWithAck(topics, subscriber).thenApply(ack -> null);
    }

    @Override
    public CompletionStage<Void> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels,
            final ActorRef subscriber,
            @Nullable final String group) {
        if (acknowledgementLabels.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // don't complete the future with the exception this method emits as this is a bug in Ditto which we must escalate
        // via the actor supervision strategy
        ensureAcknowledgementLabelsAreFullyResolved(acknowledgementLabels);

        return distributedAcks.declareAcknowledgementLabels(acknowledgementLabels, subscriber, group)
                .thenApply(ack -> null);
    }

    private static void ensureAcknowledgementLabelsAreFullyResolved(final Collection<AcknowledgementLabel> ackLabels) {
        ackLabels.stream()
                .filter(Predicate.not(AcknowledgementLabel::isFullyResolved))
                .findFirst()
                .ifPresent(ackLabel -> {
                    // if this happens, this is a bug in the Ditto codebase! at this point the AckLabel must be resolved
                    throw new IllegalArgumentException("AcknowledgementLabel was not fully resolved while " +
                            "trying to declare it: " + ackLabel);
                });
    }

    @Override
    public void removeAcknowledgementLabelDeclaration(final ActorRef subscriber) {
        distributedAcks.removeAcknowledgementLabelDeclaration(subscriber);
    }

    private CompletionStage<Void> partitionByStreamingTypes(final Collection<StreamingType> types,
            final Function<Set<StreamingType>, CompletionStage<?>> onLiveSignals,
            final Function<Boolean, CompletionStage<?>> onTwinEvents,
            final Function<Boolean, CompletionStage<?>> onPolicyAnnouncement) {
        final Set<StreamingType> liveTypes;
        final boolean hasTwinEvents;
        final boolean hasPolicyAnnouncements;
        if (types.isEmpty()) {
            liveTypes = Collections.emptySet();
            hasTwinEvents = false;
            hasPolicyAnnouncements = false;
        } else {
            liveTypes = EnumSet.copyOf(types);
            hasTwinEvents = liveTypes.remove(StreamingType.EVENTS);
            hasPolicyAnnouncements = liveTypes.remove(StreamingType.POLICY_ANNOUNCEMENTS);
        }
        final CompletableFuture<?> liveStage = onLiveSignals.apply(liveTypes).toCompletableFuture();
        final CompletableFuture<?> twinStage = onTwinEvents.apply(hasTwinEvents).toCompletableFuture();
        final CompletableFuture<?> policyAnnouncementStage =
                onPolicyAnnouncement.apply(hasPolicyAnnouncements).toCompletableFuture();
        return CompletableFuture.allOf(liveStage, twinStage, policyAnnouncementStage);
    }

    private static Predicate<Collection<String>> toFilter(final Collection<StreamingType> streamingTypes) {
        final Set<String> streamingTypeTopics =
                streamingTypes.stream().map(StreamingType::getDistributedPubSubTopic).collect(Collectors.toSet());
        return topics -> topics.stream().anyMatch(streamingTypeTopics::contains);
    }

    static final class ExtensionId extends AbstractExtensionId<DittoProtocolSub> {

        static final ExtensionId INSTANCE = new ExtensionId();

        private ExtensionId() {}

        @Override
        public DittoProtocolSub createExtension(final ExtendedActorSystem system) {
            final DistributedAcks distributedAcks = DistributedAcks.create(system);
            return of(system, distributedAcks);
        }
    }
}
