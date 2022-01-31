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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.mapping.AbstractMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.connectivity.service.mapping.PayloadMapper;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.connectivity.api.ExternalMessage;

/**
 * Implementation of {@link org.eclipse.ditto.connectivity.service.mapping.MessageMapper} that always drops.
 */
@PayloadMapper(alias = DroppingMessageMapper.ALIAS)
public class DroppingMessageMapper extends AbstractMessageMapper {

    static final String ALIAS = "dropping";

    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(ALIAS, Collections.emptyMap());

    @Override
    public void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        // ignore
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return Collections.emptyList();
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return Collections.emptyList();
    }
}
