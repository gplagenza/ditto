/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.api;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import akka.actor.ActorRef;
import akka.cluster.ddata.Replicator;

/**
 * Request to subscribe to topics.
 */
public final class Subscribe extends AbstractRequest {

    @Nullable private final Predicate<Collection<String>> filter;
    @Nullable private final String group;

    private Subscribe(final Collection<String> topics,
            final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency,
            final boolean acknowledge,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group) {
        super(topics, subscriber, writeConsistency, acknowledge);
        this.filter = filter;
        this.group = group;
    }

    /**
     * Create a "subscribe" request.
     *
     * @param topics the topics to subscribe to.
     * @param subscriber who is subscribing.
     * @param writeConsistency with which write consistency should this subscription be updated.
     * @param acknowledge whether acknowledgement is desired.
     * @param group any group the subscriber belongs to, or null.
     * @return the request.
     */
    public static Subscribe of(final Collection<String> topics,
            final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency,
            final boolean acknowledge,
            @Nullable final String group) {
        return new Subscribe(topics, subscriber, writeConsistency, acknowledge, null, group);
    }

    /**
     * Create a "subscribe" request.
     *
     * @param topics the topics to subscribe to.
     * @param subscriber who is subscribing.
     * @param writeConsistency with which write consistency should this subscription be updated.
     * @param acknowledge whether acknowledgement is desired.
     * @param filter local filter for incoming messages.
     * @param group any group the subscriber belongs to, or null.
     * @return the request.
     */
    public static Subscribe of(final Collection<String> topics,
            final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency,
            final boolean acknowledge,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group) {
        return new Subscribe(topics, subscriber, writeConsistency, acknowledge, filter, group);
    }

    /**
     * @return Filter for incoming messages.
     */
    @Nullable
    public Predicate<Collection<String>> getFilter() {
        return filter;
    }

    /**
     * @return the group the subscriber belongs to, or an empty optional.
     */
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

}
