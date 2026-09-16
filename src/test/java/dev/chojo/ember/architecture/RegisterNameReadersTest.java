/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.chojo.ember.feature.account.entity.Account;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Who may read the two halves of the register name, and therefore who may write a person's name
 * without asking what they are called.
 * <p>
 * Everywhere else asks {@code MemberNameResolver} or {@code NameParts} for the form it wants, so
 * that one person is spelled one way. These are the places entitled to the register itself: the
 * account's own screens and mails, where the name is read and changed; the security features, which
 * hand the halves to a browser's credential store; the exports and the data request, which are
 * records; the importers and seeders, which create people from two halves rather than reading them;
 * and the two classes that answer the question for everybody else.
 * <p>
 * This list is meant to be read, not grown. A new entry means somebody decided that a screen should
 * show a name nobody is called by, and that decision belongs here where it can be argued with.
 */
@AnalyzeClasses(packages = "dev.chojo.ember", importOptions = ImportOption.DoNotIncludeTests.class)
public class RegisterNameReadersTest {

    private static final Set<String> ENTITLED = Set.of(
            "dev.chojo.ember.api.ApiServer",
            "dev.chojo.ember.feature.account.route.AuthRoutes",
            "dev.chojo.ember.feature.account.service.AccountEmailService",
            "dev.chojo.ember.feature.account.service.AuthService",
            "dev.chojo.ember.feature.account.service.SessionInfoService",
            "dev.chojo.ember.feature.cluster.route.ClusterMemberManagementRoutes",
            "dev.chojo.ember.feature.cluster.route.ClusterMemberRoutes",
            "dev.chojo.ember.feature.inventory.route.InventoryCheckRoutes",
            "dev.chojo.ember.feature.inventory.service.InventoryExportService",
            "dev.chojo.ember.feature.inventory.service.MovementExportService",
            "dev.chojo.ember.feature.legal.service.GdprExportService",
            "dev.chojo.ember.feature.members.entity.MemberWithName",
            "dev.chojo.ember.feature.members.entity.NameParts",
            "dev.chojo.ember.feature.members.route.MemberRoutes",
            "dev.chojo.ember.feature.members.route.StationMemberInviteRoutes",
            "dev.chojo.ember.feature.members.service.MemberImportService",
            "dev.chojo.ember.feature.members.service.MemberNameResolver",
            "dev.chojo.ember.feature.members.service.StationMemberInviteService",
            "dev.chojo.ember.feature.onboarding.service.OnboardingService",
            "dev.chojo.ember.feature.passkey.route.PasskeyAdminRoutes",
            "dev.chojo.ember.feature.passkey.route.PasskeyRoutes",
            "dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService",
            "dev.chojo.ember.feature.station.route.StationApplicationRoutes",
            "dev.chojo.ember.feature.station.route.StationManageRoutes",
            "dev.chojo.ember.feature.station.route.StationRoutes",
            "dev.chojo.ember.feature.station.service.StationApplicationService",
            "dev.chojo.ember.feature.station.service.StationService",
            "dev.chojo.ember.feature.system.route.AdminSettingsRoutes",
            "dev.chojo.ember.feature.system.service.DemoAvatarSeeder",
            "dev.chojo.ember.feature.system.service.DemoMemberSeeder",
            "dev.chojo.ember.feature.twofactor.route.TwoFactorAdminRoutes",
            "dev.chojo.ember.feature.twofactor.service.TwoFactorPolicyService");

    private static final DescribedPredicate<JavaClass> NOT_ENTITLED =
            new DescribedPredicate<>("are not entitled to the register name") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !ENTITLED.contains(topLevelNameOf(javaClass));
                }
            };

    /**
     * The name of the file a class lives in, so that a lambda or a nested record is judged by the
     * class that owns it rather than by its own generated name.
     */
    private static String topLevelNameOf(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    @ArchTest
    static final ArchRule onlyTheEntitledReadTheRegisterName = noClasses()
            .that(NOT_ENTITLED)
            .should()
            .callMethod(Account.class, "firstName")
            .orShould()
            .callMethod(Account.class, "lastName")
            .because("a name is asked for by the form it is wanted in, not built from its halves");
}
