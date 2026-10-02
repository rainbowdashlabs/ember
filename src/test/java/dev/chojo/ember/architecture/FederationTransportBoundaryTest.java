/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationContractRefreshService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Whether a partner runs on this instance or on another one is the federation transport's question
 * alone. A feature asks its partner through the transport and gets the same answer either way, so
 * a feature that branches on where the partner runs has two paths that drift apart.
 *
 * <p>Three classes keep the question because it is about the wire itself, not about what a partner
 * is asked: the contract check and the contract refresh only concern partners that speak over HTTP,
 * since a partner on this instance runs the same build, and webhook delivery is the HTTP side of
 * the transport's notifications.
 */
@AnalyzeClasses(packages = "dev.chojo.ember", importOptions = ImportOption.DoNotIncludeTests.class)
public class FederationTransportBoundaryTest {

    @ArchTest
    static final ArchRule onlyTheTransportAsksWhereAPartnerRuns = noClasses()
            .that()
            .resideOutsideOfPackage("..federation.transport..")
            .and()
            .doNotBelongToAnyOf(
                    FederationPartner.class,
                    FederationService.class,
                    FederationContractRefreshService.class,
                    FederationWebhookService.class)
            .should()
            .accessTargetWhere(describe(
                    "FederationPartner.isRemote",
                    (JavaAccess<?> access) -> access.getTargetOwner().isEquivalentTo(FederationPartner.class)
                            && access.getName().equals("isRemote")));
}
