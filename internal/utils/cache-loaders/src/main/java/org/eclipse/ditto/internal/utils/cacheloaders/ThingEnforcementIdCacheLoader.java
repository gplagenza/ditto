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
package org.eclipse.ditto.internal.utils.cacheloaders;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Loads entity ID relation for authorization of a Thing by asking the things-shard-region proxy.
 */
@Immutable
public final class ThingEnforcementIdCacheLoader
        implements AsyncCacheLoader<EnforcementCacheKey, Entry<EnforcementCacheKey>> {

    private final ActorAskCacheLoader<EnforcementCacheKey, Command<?>, EnforcementContext> delegate;

    /**
     * Constructor.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param shardRegionProxy the shard-region-proxy.
     */
    public ThingEnforcementIdCacheLoader(final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final ActorRef shardRegionProxy) {

        delegate = ActorAskCacheLoader.forShard(askWithRetryConfig,
                scheduler,
                ThingConstants.ENTITY_TYPE,
                shardRegionProxy,
                (entityId, enforcementContext) -> ThingCommandFactory.sudoRetrieveThing(entityId),
                ThingEnforcementIdCacheLoader::handleSudoRetrieveThingResponse);
    }

    @Override
    public CompletableFuture<Entry<EnforcementCacheKey>> asyncLoad(final EnforcementCacheKey key,
            final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    private static Entry<EnforcementCacheKey> handleSudoRetrieveThingResponse(final Object response,
            @Nullable final EnforcementContext context) {
        if (response instanceof SudoRetrieveThingResponse) {
            final var sudoRetrieveThingResponse = (SudoRetrieveThingResponse) response;
            final var thing = sudoRetrieveThingResponse.getThing();
            final long revision = thing.getRevision().map(ThingRevision::toLong)
                    .orElseThrow(badThingResponse("no revision"));
            final var policyId = thing.getPolicyEntityId().orElseThrow(badThingResponse("no PolicyId"));
            final PersistenceLifecycle persistenceLifecycle =
                    thing.getLifecycle().map(Enum::name).flatMap(PersistenceLifecycle::forName).orElse(null);
            final EnforcementContext newEnforcementContext = EnforcementContext.of(persistenceLifecycle);
            final var resourceKey = EnforcementCacheKey.of(policyId, newEnforcementContext);
            return Entry.of(revision, resourceKey);
        } else if (response instanceof ThingNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrieveThingResponse, got: " + response);
        }
    }

    private static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }

}
