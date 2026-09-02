/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chojo.ember.feature.inventory.entity.ResolvedTarget;

import java.time.Instant;
import java.util.List;

/**
 * What one station can put its hands on over one window, and everything that is already spoken for.
 *
 * <p>Over-claiming is allowed and shown rather than prevented. Two appointments may both need the
 * last trailer on the same weekend, and a tool that refuses to record that does not remove the
 * conflict, it hides it until the Saturday. So {@link #free()} goes negative and {@link #claims()}
 * names who is involved.
 *
 * @param target what was asked about
 * @param from   the first moment of the window
 * @param to     the last moment of the window
 * @param stock  how many pieces the station could bring along at all
 * @param claims everything holding some of that stock over this window
 */
public record EquipmentAvailability(
        ResolvedTarget target, Instant from, Instant to, int stock, List<EquipmentClaim> claims) {

    /**
     * How many pieces the claims take between them.
     *
     * @return the claimed count
     */
    @JsonProperty("claimed")
    public int claimed() {
        return claims.stream().mapToInt(EquipmentClaim::quantity).sum();
    }

    /**
     * What is left, which goes below zero when more has been promised than exists.
     *
     * @return the free count
     */
    @JsonProperty("free")
    public int free() {
        return stock - claimed();
    }

    /**
     * Whether more is spoken for than the station has.
     *
     * @return {@code true} when the window is over-claimed
     */
    @JsonProperty("overClaimed")
    public boolean overClaimed() {
        return claimed() > stock;
    }
}
