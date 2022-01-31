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
package org.eclipse.ditto.concierge.service.enforcement.placeholders.references;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Responsible to resolve a field of a referenced entity.
 *
 * @param <T> The type of the field.
 */
public interface ReferencePlaceholderResolver<T> {

    CompletionStage<T> resolve(ReferencePlaceholder referencePlaceholder, DittoHeaders dittoHeaders);
}
