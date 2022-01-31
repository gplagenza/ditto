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
package org.eclipse.ditto.connectivity.service.messaging;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.bson.Document;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectivityInternalErrorException;
import org.eclipse.ditto.connectivity.service.config.ConnectionIdsRetrievalConfig;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityErrorResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIds;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIdsResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor handling messages related to connections e.g. retrieving all connections ids.
 */
public final class ConnectionIdsRetrievalActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "connectionIdsRetrieval";

    private static final String PERSISTENCE_ID_FIELD = "_id";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Supplier<Source<Document, NotUsed>> persistenceIdsFromJournalSourceSupplier;
    private final Supplier<Source<Document, NotUsed>> persistenceIdsFromSnapshotSourceSupplier;
    private final Materializer materializer;

    @SuppressWarnings("unused")
    private ConnectionIdsRetrievalActor(final MongoReadJournal readJournal,
            final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig) {
        materializer = Materializer.createMaterializer(this::getContext);
        persistenceIdsFromJournalSourceSupplier =
                () -> readJournal.getLatestJournalEntries(connectionIdsRetrievalConfig.getReadJournalBatchSize(),
                        Duration.ofSeconds(1), materializer);
        persistenceIdsFromSnapshotSourceSupplier =
                () -> readJournal.getNewestSnapshotsAbove("", connectionIdsRetrievalConfig.getReadSnapshotBatchSize(),
                        materializer);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param readJournal readJournal to extract current PIDs from.
     * @param connectionIdsRetrievalConfig the config to build the pid suppliers from.
     * @return the Akka configuration Props object.
     */
    public static Props props(final MongoReadJournal readJournal,
            final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig) {
        return Props.create(ConnectionIdsRetrievalActor.class, readJournal, connectionIdsRetrievalConfig);
    }

    private static boolean isDeleted(final Document document) {
        return Optional.ofNullable(document.getString(MongoReadJournal.J_EVENT_MANIFEST))
                .map(ConnectionDeleted.TYPE::equals)
                .orElse(true);
    }

    private static boolean isNotDeleted(final Document document) {
        return Optional.ofNullable(document.getString(MongoReadJournal.J_EVENT_MANIFEST))
                .map(manifest -> !ConnectionDeleted.TYPE.equals(manifest))
                .orElse(false);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveAllConnectionIds.class, this::getAllConnectionIDs)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void getAllConnectionIDs(final WithDittoHeaders cmd) {
        try {
            final Source<String, NotUsed> idsFromSnapshots = getIdsFromSnapshotsSource();
            final Source<String, NotUsed> idsFromJournal = persistenceIdsFromJournalSourceSupplier.get()
                    .filter(ConnectionIdsRetrievalActor::isNotDeleted)
                    .map(document -> document.getString(MongoReadJournal.J_EVENT_PID));

            final CompletionStage<CommandResponse> retrieveAllConnectionIdsResponse =
                    persistenceIdsFromJournalSourceSupplier.get()
                            .filter(ConnectionIdsRetrievalActor::isDeleted)
                            .map(document -> document.getString(MongoReadJournal.J_EVENT_PID))
                            .runWith(Sink.seq(), materializer)
                            .thenCompose(deletedIdsFromJournal -> idsFromSnapshots.concat(idsFromJournal)
                                    .filter(pid -> !deletedIdsFromJournal.contains(pid))
                                    .filter(pid -> pid.startsWith(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX))
                                    .map(pid -> pid.substring(
                                            ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX.length()))
                                    .runWith(Sink.seq(), materializer))
                            .thenApply(HashSet::new)
                            .thenApply(ids -> buildResponse(cmd, ids))
                            .exceptionally(throwable -> buildErrorResponse(throwable, cmd.getDittoHeaders()));
            Patterns.pipe(retrieveAllConnectionIdsResponse, getContext().dispatcher()).to(getSender());
        } catch (final Exception e) {
            log.info("Failed to load persistence ids from journal/snapshots.", e);
            getSender().tell(buildErrorResponse(e, cmd.getDittoHeaders()), getSelf());
        }
    }

    private Source<String, NotUsed> getIdsFromSnapshotsSource() {
        return persistenceIdsFromSnapshotSourceSupplier.get()
                .map(this::extractPersistenceIdFromDocument)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    private CommandResponse buildResponse(final WithDittoHeaders cmd, final Set<String> ids) {
        return RetrieveAllConnectionIdsResponse.of(ids, cmd.getDittoHeaders());
    }

    private ConnectivityErrorResponse buildErrorResponse(final Throwable throwable, final DittoHeaders dittoHeaders) {
        final ConnectivityInternalErrorException dittoRuntimeException =
                ConnectivityInternalErrorException.newBuilder()
                        .message(() -> "Failed to load connections ids from persistence: " + throwable.getMessage())
                        .dittoHeaders(dittoHeaders)
                        .build();
        return ConnectivityErrorResponse.of(dittoRuntimeException);
    }

    private Optional<String> extractPersistenceIdFromDocument(final Document document) {
        return Optional.ofNullable(document.getString(PERSISTENCE_ID_FIELD));
    }
}
