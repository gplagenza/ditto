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
package org.eclipse.ditto.internal.utils.cache;

import java.util.Optional;

import org.eclipse.ditto.base.model.entity.id.EntityId;

/**
 * Entity ID together with resource type.
 */
public interface CacheKey<C extends CacheLookupContext> {

    /**
     * Retrieve the ID.
     *
     * @return the ID.
     */
    EntityId getId();

    /**
     * Retrieve the optional context to use when doing the cache lookup.
     *
     * @return the cache context to use for lookup.
     */
    Optional<C> getCacheLookupContext();

    /**
     * Serialize this object as string.
     *
     * @return serialized form of this object.
     */
    String toString();

}
