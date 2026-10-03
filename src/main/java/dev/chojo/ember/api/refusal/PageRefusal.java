/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#PAGES}: pages.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum PageRefusal implements Refusal {
    /** A member-list cell whose description of who to show could not be read. */
    PAGE_MEMBER_LIST_NOT_READ(
            13, HttpStatus.BAD_REQUEST, "What was sent could not be read, so no member list was worked out"),

    /** A page written down without a title. */
    PAGE_NEEDS_A_TITLE(14, HttpStatus.BAD_REQUEST, Sentences.PAGE_NEEDS_A_TITLE),

    /** A page refused while it was being created, for a reason the reader cannot act on. */
    PAGE_NOT_CREATED(15, HttpStatus.BAD_REQUEST, "The page could not be created, so nothing was saved"),

    /** A page whose title was emptied while it was being saved. */
    PAGE_TITLE_MISSING_ON_SAVE(16, HttpStatus.BAD_REQUEST, Sentences.PAGE_NEEDS_A_TITLE),

    /** A page saved without the address it is reached at. */
    PAGE_ADDRESS_MISSING_ON_SAVE(
            17, HttpStatus.BAD_REQUEST, "A page needs an address of its own, so nothing was saved"),

    /** A page whose visibility was changed without saying what to. */
    PAGE_VISIBILITY_MISSING(18, HttpStatus.BAD_REQUEST, "Say who may see the page, so nothing was changed"),

    /** A share link replaced from a screen that was still showing the link before it. */
    PAGE_LINK_ALREADY_REPLACED(
            19,
            HttpStatus.CONFLICT,
            "This page has been given a different link since you last looked, so nothing was changed"),

    /** A page that cannot be the one the site opens on, named as the one it opens on. */
    LANDING_PAGE_NOT_SET(
            20, HttpStatus.BAD_REQUEST, "That page cannot be the one the site opens on, so nothing was changed"),

    /** An upload from the page editor that arrived with no file in it. */
    PAGE_UPLOAD_MISSING_FILE(21, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A file from the page editor heavier than the instance takes. */
    PAGE_UPLOAD_TOO_LARGE(22, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** A file from the page editor the station had no room for. */
    PAGE_UPLOAD_NOT_SAVED(23, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** A file from the page editor that could not be worked through at all. */
    PAGE_UPLOAD_NOT_PROCESSED(24, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_PROCESSED),

    /** A page that could not be read back after being saved, with the title, link and cells already in. */
    PAGE_NOT_HERE_AFTER_SAVE(25, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A page that could not be read back after how far it reaches was changed, with the change already in. */
    PAGE_NOT_HERE_AFTER_VISIBILITY_CHANGE(
            26, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A page that went between the editor being opened and the save being asked for. */
    PAGE_NOT_HERE_ON_SAVE(1, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page whose contents were read and then refused for what they said. */
    PAGE_NOT_SAVED(2, HttpStatus.BAD_REQUEST, "The page could not be saved"),

    /** A page that went before a copy of it could be made. */
    PAGE_NOT_HERE_ON_COPY(3, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page that was already gone when its deletion was asked for. */
    PAGE_NOT_HERE_ON_DELETE(4, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page that went before how far it reaches could be written. */
    PAGE_NOT_HERE_ON_VISIBILITY_CHANGE(5, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A public address no page of that station sits at. */
    PUBLIC_PAGE_NOT_HERE(6, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A public page that exists but has nothing drawn to serve. */
    PUBLIC_PAGE_NOT_RENDERED(7, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A station whose public site has no page to open on. */
    PUBLIC_LANDING_PAGE_NOT_HERE(8, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** Public pages at all, at a station that has switched them off. */
    PUBLIC_PAGES_SWITCHED_OFF(9, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A station that is not here, asked for by a public page address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_PAGE(10, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A share link that leads nowhere, whether because no page answers to it or because the station
     * behind it has closed its public pages. One code deliberately: the reader holds a link and is
     * owed nothing about which of the two it was.
     */
    PAGE_LINK_UNKNOWN(11, HttpStatus.NOT_FOUND, "No page is reached by this link"),

    /** A page filed under one that is reached by its link alone. */
    PAGE_UNDER_A_LINK_ONLY_PAGE(
            27,
            HttpStatus.BAD_REQUEST,
            "A page reached by its link alone cannot hold pages under it, so nothing was saved"),

    /** A page reached by its link alone, filed under another page. */
    PAGE_LINK_ONLY_UNDER_ANOTHER(
            28,
            HttpStatus.BAD_REQUEST,
            "A page reached by its link alone does not sit under another, so nothing was saved"),

    /** A page with pages under it, made reachable by its link alone. */
    PAGE_WITH_CHILDREN_NOT_LINK_ONLY(
            29,
            HttpStatus.BAD_REQUEST,
            "A page with pages under it cannot be reached by its link alone, so nothing was changed"),

    /** A new link asked for on a page nobody outside the station can open. */
    PAGE_LINK_NOT_FOR_A_CLOSED_PAGE(
            30, HttpStatus.BAD_REQUEST, "Nobody outside can open this page, so it has no link to replace"),

    /** The page the site opens on, named from another station's pages. */
    LANDING_PAGE_ELSEWHERE(31, HttpStatus.BAD_REQUEST, "That page belongs to another station, so nothing was changed"),

    /** The page the site opens on, named from pages that are not public. */
    LANDING_PAGE_NOT_PUBLIC(
            32, HttpStatus.BAD_REQUEST, "The page the site opens on has to be public, so nothing was changed"),

    /** The page the site opens on, named from pages under another page. */
    LANDING_PAGE_UNDER_ANOTHER(
            33,
            HttpStatus.BAD_REQUEST,
            "The page the site opens on cannot sit under another page, so nothing was changed"),

    /** A page filed deeper than pages go. */
    PAGE_TREE_TOO_DEEP(34, HttpStatus.BAD_REQUEST, "Pages go at most three levels deep, so nothing was saved"),

    /** A block that only a page may hold, put into a news entry or a knowledge base article. */
    CONTENT_BLOCK_ONLY_ON_PAGES(
            35,
            HttpStatus.BAD_REQUEST,
            "That block can only be used on a page, not in a news entry or an article, so nothing was saved"),

    /** A block that only a printed letter holds, such as a signature line, put into a page, news or an article. */
    CONTENT_BLOCK_ONLY_IN_LETTERS(
            36, HttpStatus.BAD_REQUEST, "That block can only be used in a letter template, so nothing was saved");

    private final Definition definition;

    PageRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.PAGES, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
