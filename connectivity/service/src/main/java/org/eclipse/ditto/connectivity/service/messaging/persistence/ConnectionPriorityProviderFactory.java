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

import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;

import akka.actor.ActorRef;

/**
 * Creates a connection priority provider based on the connection persistence actor and its logger.
 */
public interface ConnectionPriorityProviderFactory {

    /**
     * Creates a connection priority provider based on the connection persistence actor and its logger.
     *
     * @param connectionPersistenceActor the connection persistence actor.
     * @param log the logger of the connection persistence actor.
     * @return the new provider.
     */
    ConnectionPriorityProvider newProvider(ActorRef connectionPersistenceActor, DittoDiagnosticLoggingAdapter log);

}
