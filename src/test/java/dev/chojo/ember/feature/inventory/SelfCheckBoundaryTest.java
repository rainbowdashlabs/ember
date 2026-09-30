/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.chojo.ember.api.RegisteredRoutes;
import dev.chojo.ember.api.RegisteredRoutes.Route;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.route.InventoryCheckRoutes;
import dev.chojo.ember.feature.inventory.route.SelfCheckRoutes;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a member's own endpoints may not do, checked as a refusal rather than trusted to the screen.
 *
 * <p>A screen that simply does not offer the button is no control at all: the address is still
 * there and still answers. The two halves of the control are that everything which settles a check
 * is registered behind the check permission, and that the member's own endpoints cannot reach any
 * of it even if somebody wires them up to. The first half is read from the router the application
 * builds, the second from the compiled classes.
 */
class SelfCheckBoundaryTest {

    /**
     * The services that settle something: they hand a piece over, write one down, put a record
     * right or close a check. None of them belongs on an endpoint a member reaches.
     */
    private static final Set<String> SETTLING_SERVICES =
            Set.of("InventoryService", "ItemCustodyService", "ProcurementService", "ItemMovementService");

    /** The methods that settle something, named as the walk's own routes call them. */
    private static final Set<String> SETTLING_CALLS = Set.of(
            "assignItem",
            "createItem",
            "createAndHandOut",
            "completeCheck",
            "completeContainerCheck",
            "correct",
            "markLost",
            "markFound",
            "take",
            "refuse",
            "finish");

    private static JavaClasses inventory;

    @BeforeAll
    static void importTheFeature() {
        inventory = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.chojo.ember.feature.inventory");
    }

    private static List<Route> routesOf(Class<?> owner) {
        List<Route> routes = RegisteredRoutes.application().routes().stream()
                .filter(route -> route.owner() == owner)
                .toList();
        assertFalse(routes.isEmpty(), owner.getSimpleName() + " registers routes");
        return routes;
    }

    @Test
    void everyEndpointOfTheWalkAsksForTheCheckPermission() {
        List<Route> open = routesOf(InventoryCheckRoutes.class).stream()
                .filter(route -> !route.roles().contains(StationPermission.INVENTORY_CHECK))
                .toList();
        assertTrue(open.isEmpty(), () -> "endpoint(s) of the walk that do not ask for the check permission: " + open);
    }

    @Test
    void aMembersOwnEndpointsAskForNothingButBeingAMember() {
        List<Route> wrong = routesOf(SelfCheckRoutes.class).stream()
                .filter(route -> route.roles().contains(StationPermission.INVENTORY_CHECK)
                        == route.roles().contains(StationPermission.USER))
                .toList();
        assertTrue(
                wrong.isEmpty(),
                () -> "self-check endpoint(s) declaring neither the check permission nor plain"
                        + " membership, or both: " + wrong);
    }

    @Test
    void theMembersEndpointsCannotReachAnythingThatSettles() {
        List<String> reachable = classAndNested(SelfCheckRoutes.class).stream()
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getSimpleName())
                .filter(SETTLING_SERVICES::contains)
                .distinct()
                .toList();
        assertTrue(reachable.isEmpty(), () -> "SelfCheckRoutes can reach service(s) that settle a check: " + reachable);
    }

    @Test
    void theMembersServiceSettlesNothingEither() {
        List<String> calls = classAndNested(SelfCheckService.class).stream()
                .flatMap(type -> type.getMethodCallsFromSelf().stream())
                .filter(call -> SETTLING_CALLS.contains(call.getName()))
                .map(call -> call.getDescription())
                .toList();
        assertTrue(calls.isEmpty(), () -> "SelfCheckService calls something that settles a check: " + calls);
    }

    private static List<JavaClass> classAndNested(Class<?> owner) {
        return inventory.stream()
                .filter(type ->
                        type.getName().equals(owner.getName()) || type.getName().startsWith(owner.getName() + "$"))
                .toList();
    }
}
