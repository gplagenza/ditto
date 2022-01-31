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
package org.eclipse.ditto.concierge.service.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.concierge.service.common.DefaultEntityCreationConfig;
import org.eclipse.ditto.concierge.service.enforcement.CreationRestrictionEnforcer.Context;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.things.model.ThingConstants;
import org.junit.jupiter.api.Test;

import com.typesafe.config.ConfigFactory;

public final class CreationRestrictionEnforcerTest {

    private static CreationRestrictionEnforcer load(final String basename) {
        var config = ConfigFactory.load(basename);
        return DefaultCreationRestrictionEnforcer.of(DefaultEntityCreationConfig.of(config));
    }

    private static DittoHeaders identity(final String... subjects) {
        var authCtx = AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                Arrays.stream(subjects).map(AuthorizationSubject::newInstance)
                        .collect(Collectors.toUnmodifiableList())
        );

        return DittoHeaders.of(Map.of(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), authCtx.toJsonString()));
    }

    @Test
    public void testDefault() {
        var enforcer = load("entity-creation/default");

        testCanCreate(enforcer,
                PolicyConstants.ENTITY_TYPE, "ns1", identity("keycloak:some-user"),
                true);
        testCanCreate(enforcer,
                ThingConstants.ENTITY_TYPE, "ns1", identity("keycloak:some-user"),
                true);
    }

    @Test
    public void testRestricted() {
        var enforcer = load("entity-creation/restricted1");

        testCanCreate(enforcer,
                PolicyConstants.ENTITY_TYPE, "ns1", identity("keycloak:some-user"),
                false);
        testCanCreate(enforcer,
                ThingConstants.ENTITY_TYPE, "ns1", identity("keycloak:some-user"),
                false);

        testCanCreate(enforcer,
                PolicyConstants.ENTITY_TYPE, "ns1", identity("keycloak:some-admin"),
                false);
        testCanCreate(enforcer,
                ThingConstants.ENTITY_TYPE, "ns1", identity("keycloak:some-admin"),
                true);

        testCanCreate(enforcer,
                PolicyConstants.ENTITY_TYPE, "some-ns-1", identity("keycloak:some-user"),
                true);
        testCanCreate(enforcer,
                ThingConstants.ENTITY_TYPE, "some-ns-1", identity("keycloak:some-user"),
                false);
    }

    private void testCanCreate(final CreationRestrictionEnforcer enforcer, final EntityType type,
            final String namespace, final DittoHeaders headers, boolean expectedOutcome) {

        assertEquals(expectedOutcome, enforcer.canCreate(new Context(
                type.toString(),
                namespace,
                headers
        )));

    }

}
