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

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;

/**
 * Handles substitution for {@link org.eclipse.ditto.policies.model.SubjectId} inside a {@link ModifySubject} command.
 */
final class ModifySubjectSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifySubject> {

    ModifySubjectSubstitutionStrategy() {
        super(ModifySubject.class);
    }

    @Override
    public DittoHeadersSettable<?> apply(final ModifySubject modifySubject,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifySubject);
        requireNonNull(substitutionAlgorithm);

        final String subjectId = modifySubject.getSubject().getId().toString();
        final String substitutedSubjectId = substitutionAlgorithm.substitute(subjectId, modifySubject);

        if (subjectId.equals(substitutedSubjectId)) {
            return modifySubject;
        } else {
            final Subject newSubject =
                    Subject.newInstance(substitutedSubjectId, modifySubject.getSubject().getType());
            return ModifySubject.of(modifySubject.getEntityId(), modifySubject.getLabel(), newSubject,
                    modifySubject.getDittoHeaders());
        }
    }
}
