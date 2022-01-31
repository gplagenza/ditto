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
package org.eclipse.ditto.things.api;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Things messaging.
 */
@Immutable
public final class ThingsMessagingConstants {

    @SuppressWarnings("squid:S1075")
    private static final String USER_PATH = "/user";

    /**
     * Path of the root actor.
     */
    public static final String ROOT_ACTOR_PATH = USER_PATH + "/thingsRoot";

    /**
     * Path of the actor that streams from the event journal.
     */
    public static final String THINGS_STREAM_PROVIDER_ACTOR_PATH = ROOT_ACTOR_PATH + "/persistenceStreamingActor";

    /**
     * Path of the actor that streams from the snapshot store.
     */
    public static final String THINGS_SNAPSHOT_STREAMING_ACTOR_PATH = ROOT_ACTOR_PATH + "/snapshotStreamingActor";

    /**
     * Name of the shard region for Thing entities.
     */
    public static final String SHARD_REGION = "thing";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "things";

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsMessagingConstants() {
        // no-op
    }
}
