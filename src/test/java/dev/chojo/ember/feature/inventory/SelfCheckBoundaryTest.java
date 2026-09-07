/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a member's own endpoints may not do, checked as a refusal rather than trusted to the screen.
 *
 * <p>A screen that simply does not offer the button is no control at all: the address is still
 * there and still answers. The two halves of the control are that everything which settles a check
 * is registered behind the check permission, and that the member's own endpoints cannot reach any
 * of it even if somebody wires them up to.
 */
class SelfCheckBoundaryTest {

    private static final Path ROUTES = Path.of("src", "main", "java", "dev", "chojo", "ember", "feature", "inventory");

    private static final Pattern REGISTRATION =
            Pattern.compile("routes\\.(get|post|put|patch|delete)\\(\\s*prefix\\s*\\+\\s*\"([^\"]*)\"([^;]*);");

    /**
     * The services that settle something: they hand a piece over, write one down, put a record
     * right or close a check. None of them belongs on an endpoint a member reaches.
     */
    private static final List<String> SETTLING_SERVICES =
            List.of("InventoryService", "ItemCustodyService", "ProcurementService", "ExchangeService");

    /**
     * The methods that settle something, named as the walk's own routes call them.
     */
    private static final List<String> SETTLING_CALLS = List.of(
            ".assignItem(",
            ".createItem(",
            ".createAndHandOut(",
            ".completeCheck(",
            ".completeContainerCheck(",
            ".correct(",
            ".markLost(",
            ".markFound(",
            ".take(",
            ".refuse(",
            ".finish(");

    private static String source(String routeClass) throws IOException {
        return Files.readString(ROUTES.resolve("route").resolve(routeClass + ".java"));
    }

    @Test
    void everyEndpointOfTheWalkAsksForTheCheckPermission() throws IOException {
        List<String> open = new ArrayList<>();
        Matcher registration = REGISTRATION.matcher(source("InventoryCheckRoutes"));
        while (registration.find()) {
            if (!registration.group(3).contains("StationPermission.INVENTORY_CHECK")) {
                open.add(registration.group(1) + " " + registration.group(2));
            }
        }
        assertTrue(open.isEmpty(), () -> "endpoint(s) of the walk that do not ask for the check permission: " + open);
    }

    @Test
    void aMembersOwnEndpointsAskForNothingButBeingAMember() throws IOException {
        List<String> wrong = new ArrayList<>();
        Matcher registration = REGISTRATION.matcher(source("SelfCheckRoutes"));
        while (registration.find()) {
            String declared = registration.group(3);
            boolean handOut = declared.contains("StationPermission.INVENTORY_CHECK");
            boolean member = declared.contains("StationPermission.USER");
            if (handOut == member) {
                wrong.add(registration.group(1) + " " + registration.group(2));
            }
        }
        assertTrue(
                wrong.isEmpty(),
                () -> "self-check endpoint(s) declaring neither the check permission nor plain"
                        + " membership, or both: " + wrong);
    }

    @Test
    void theMembersEndpointsCannotReachAnythingThatSettles() throws IOException {
        String routes = source("SelfCheckRoutes");
        List<String> reachable =
                SETTLING_SERVICES.stream().filter(routes::contains).toList();
        assertTrue(reachable.isEmpty(), () -> "SelfCheckRoutes can reach service(s) that settle a check: " + reachable);
    }

    @Test
    void theMembersServiceSettlesNothingEither() throws IOException {
        String service = Files.readString(ROUTES.resolve("service").resolve("SelfCheckService.java"));
        List<String> calls = SETTLING_CALLS.stream().filter(service::contains).toList();
        assertTrue(calls.isEmpty(), () -> "SelfCheckService calls something that settles a check: " + calls);
    }
}
