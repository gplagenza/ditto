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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayJwtIssuerNotSupportedException;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PipelineElement;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.policies.model.SubjectId;

/**
 * Implementation of {@link JwtAuthorizationSubjectsProvider} for Google JWTs.
 */
@Immutable
public final class DittoJwtAuthorizationSubjectsProvider implements JwtAuthorizationSubjectsProvider {

    private final JwtSubjectIssuersConfig jwtSubjectIssuersConfig;

    private DittoJwtAuthorizationSubjectsProvider(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig) {
        this.jwtSubjectIssuersConfig = jwtSubjectIssuersConfig;
    }

    /**
     * Returns a new {@code DittoAuthorizationSubjectsProvider}.
     *
     * @param jwtSubjectIssuersConfig the subject issuer configuration.
     * @return the DittoAuthorizationSubjectsProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DittoJwtAuthorizationSubjectsProvider of(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig) {
        checkNotNull(jwtSubjectIssuersConfig);
        return new DittoJwtAuthorizationSubjectsProvider(jwtSubjectIssuersConfig);
    }

    @Override
    public List<AuthorizationSubject> getAuthorizationSubjects(final JsonWebToken jsonWebToken) {
        checkNotNull(jsonWebToken);

        final String issuer = jsonWebToken.getIssuer();
        final JwtSubjectIssuerConfig jwtSubjectIssuerConfig = jwtSubjectIssuersConfig.getConfigItem(issuer)
                .orElseThrow(() -> GatewayJwtIssuerNotSupportedException.newBuilder(issuer).build());

        final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(JwtPlaceholder.getInstance(), jsonWebToken));

        return jwtSubjectIssuerConfig.getAuthorizationSubjectTemplates().stream()
            .map(expressionResolver::resolve)
            .map(PipelineElement::toOptional)
            .flatMap(Optional::stream)
            .flatMap(JwtPlaceholder::expandJsonArraysInResolvedSubject)
            .map(subject -> SubjectId.newInstance(jwtSubjectIssuerConfig.getSubjectIssuer(), subject))
            .map(AuthorizationSubject::newInstance)
            .collect(Collectors.toList());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoJwtAuthorizationSubjectsProvider that = (DittoJwtAuthorizationSubjectsProvider) o;
        return Objects.equals(jwtSubjectIssuersConfig, that.jwtSubjectIssuersConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jwtSubjectIssuersConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "jwtSubjectIssuersConfig=" + jwtSubjectIssuersConfig +
                "]";
    }

}
