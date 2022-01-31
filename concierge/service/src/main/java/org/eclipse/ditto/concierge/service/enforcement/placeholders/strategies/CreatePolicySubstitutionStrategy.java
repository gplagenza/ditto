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
package org.eclipse.ditto.concierge.service.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;

/**
 * Handles substitution for {@link org.eclipse.ditto.policies.model.SubjectId}
 * inside a {@link CreatePolicy} command.
 */
final class CreatePolicySubstitutionStrategy extends AbstractTypedSubstitutionStrategy<CreatePolicy> {

    CreatePolicySubstitutionStrategy() {
        super(CreatePolicy.class);
    }

    @Override
    public DittoHeadersSettable<?> apply(final CreatePolicy createPolicy,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(createPolicy);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = createPolicy.getDittoHeaders();
        final Policy existingPolicy = createPolicy.getPolicy();
        final Policy substitutedPolicy =
                substitutePolicy(existingPolicy, substitutionAlgorithm, dittoHeaders);

        if (existingPolicy.equals(substitutedPolicy)) {
            return createPolicy;
        } else {
            return CreatePolicy.of(substitutedPolicy, dittoHeaders);
        }
    }

}
