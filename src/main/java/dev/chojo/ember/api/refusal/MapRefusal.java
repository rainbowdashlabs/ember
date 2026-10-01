/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#MAPS}: maps.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum MapRefusal implements Refusal {
    /** A piece of the map asked for without saying which piece. */
    MAP_TILE_NUMBER_MISSING(1, HttpStatus.BAD_REQUEST, "Name the piece of the map you want"),

    /** A piece of the map tried out by an administrator and named by something that is not a number. */
    MAP_TILE_NUMBER_NOT_A_NUMBER(2, HttpStatus.BAD_REQUEST, Sentences.MAP_TILE_NOT_A_NUMBER),

    /** A piece of the map asked for on a public page and named by something that is not a number. */
    PUBLIC_MAP_TILE_NUMBER_NOT_A_NUMBER(3, HttpStatus.BAD_REQUEST, Sentences.MAP_TILE_NOT_A_NUMBER),

    /** A piece of the map asked for at a zoom this instance does not serve. */
    MAP_TILE_ZOOM_OUT_OF_RANGE(4, HttpStatus.NOT_FOUND, "The map is not served at that zoom"),

    /** A piece of the map whose column or row lies outside the map at its zoom. */
    MAP_TILE_OFF_THE_MAP(5, HttpStatus.NOT_FOUND, "That piece lies outside the map"),

    /** Pieces of the map asked for faster than one address may ask for them. */
    MAP_TILES_TOO_OFTEN(6, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A map setting saved without choosing where the map pictures come from. */
    MAP_TILE_PROVIDER_MISSING(7, HttpStatus.BAD_REQUEST, "Choose where the map comes from, so nothing was saved"),

    /** A map setting whose closest and farthest zoom do not make a range the map is served at. */
    MAP_ZOOM_RANGE_NOT_GOOD(
            8,
            HttpStatus.BAD_REQUEST,
            "The zoom has to run from 0 to at most 22, smallest first, so nothing was saved"),

    /** A map provider that needs a key, chosen without one. */
    MAP_TILE_PROVIDER_KEY_MISSING(9, HttpStatus.BAD_REQUEST, "That map provider needs a key, so nothing was saved"),

    /** A map of its own chosen without the address its pictures are fetched from. */
    MAP_TILE_ADDRESS_MISSING(
            10,
            HttpStatus.BAD_REQUEST,
            "A map of your own needs the address its pictures come from, so nothing was saved"),

    /** An address search setting saved without choosing who answers the search. */
    MAP_GEOCODING_PROVIDER_MISSING(11, HttpStatus.BAD_REQUEST, "Choose who looks up addresses, so nothing was saved"),

    /** A map cache size below nothing or above what an instance may keep. */
    MAP_TILE_CACHE_SIZE_OUT_OF_RANGE(
            12, HttpStatus.BAD_REQUEST, "The map cache has to be between 0 and 10000 MB, so nothing was saved");

    private final Definition definition;

    MapRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.MAPS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
