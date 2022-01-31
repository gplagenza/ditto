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
package org.eclipse.ditto.internal.utils.pubsub;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;

/**
 * Package-private extractor of policy announcement topics.
 */
final class PolicyAnnouncementTopicExtractor implements PubSubTopicExtractor<PolicyAnnouncement<?>> {

    @Override
    public Collection<String> getTopics(final PolicyAnnouncement<?> message) {
        if (message instanceof SubjectDeletionAnnouncement) {
            final SubjectDeletionAnnouncement announcement = (SubjectDeletionAnnouncement) message;
            return announcement.getSubjectIds()
                    .stream()
                    .map(SubjectId::toString)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
