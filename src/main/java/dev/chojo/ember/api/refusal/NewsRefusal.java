/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#NEWS}: news.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum NewsRefusal implements Refusal {
    /**
     * An entry that is not here, or one the reader was never among the people it was addressed to.
     *
     * <p>One code for both on purpose. Splitting them would tell a reader that an entry exists
     * which they were never meant to know about.
     */
    NEWS_NOT_HERE_OR_NOT_YOURS(1, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An entry written without a title. */
    NEWS_NEEDS_A_TITLE(2, HttpStatus.BAD_REQUEST, Sentences.NEWS_NEEDS_A_TITLE),

    /** An entry corrected after it had gone. */
    NEWS_NOT_HERE_ON_UPDATE(3, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry withdrawn after it had gone. */
    NEWS_NOT_DELETED(4, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** Blocks saved against an entry that has gone. */
    NEWS_NOT_HERE_ON_BLOCK_SAVE(5, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry handed to the page editor after it had gone. */
    NEWS_NOT_HERE_ON_BLOCK_SWITCH(6, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An attachment of an entry, addressed after it had gone. */
    NEWS_ATTACHMENT_NOT_HERE(7, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /** An attachment asked for without naming the file to hang on the entry. */
    NEWS_ATTACHMENT_FILE_NOT_NAMED(8, HttpStatus.BAD_REQUEST, "Name the file to hang on this entry"),

    /** An attachment given a new label after it had gone. */
    NEWS_ATTACHMENT_NOT_RELABELLED(
            9, HttpStatus.NOT_FOUND, "That attachment is not here any more, so nothing was changed"),

    /** An attachment taken off an entry after it had gone. */
    NEWS_ATTACHMENT_NOT_DETACHED(10, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /** A comment left under an entry with nothing written in it. */
    NEWS_COMMENT_NEEDS_TEXT(11, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment changed after it had gone. */
    NEWS_COMMENT_NOT_HERE_ON_UPDATE(12, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment somebody else wrote, changed. */
    NEWS_COMMENT_NOT_YOURS_TO_EDIT(13, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /** A comment changed to nothing at all. */
    NEWS_COMMENT_NEEDS_TEXT_ON_UPDATE(14, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment that went while it was being changed. */
    NEWS_COMMENT_NOT_HERE_AFTER_UPDATE(15, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment deleted after it had gone. */
    NEWS_COMMENT_NOT_HERE_ON_DELETE(16, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment somebody else wrote, deleted by a reader who does not moderate. */
    NEWS_COMMENT_NOT_YOURS_TO_DELETE(17, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** A comment that went while it was being deleted. */
    NEWS_COMMENT_NOT_DELETED(18, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A public blog address naming a station that is not here. */
    STATION_NOT_HERE_BEHIND_BLOG(19, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station behind a blog listing, gone between resolving it and reading it. */
    STATION_NOT_HERE_BEHIND_BLOG_LIST(20, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A blog listing at a station that keeps no public blog. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST(21, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /** The station behind a blog feed, gone between resolving it and reading it. */
    STATION_NOT_HERE_BEHIND_BLOG_FEED(22, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A blog feed at a station that keeps no public blog. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_FEED(23, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /** A blog feed that could not be written out. */
    BLOG_FEED_NOT_MADE(
            24, HttpStatus.INTERNAL_SERVER_ERROR, "The blog feed could not be put together. Trying again may work"),

    /** The station behind a single blog entry, gone between resolving it and reading it. */
    STATION_NOT_HERE_BEHIND_BLOG_ENTRY(25, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A single blog entry at a station that keeps no public blog. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_ENTRY(26, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /**
     * A blog entry that is not here, or one that was never put on the public blog.
     *
     * <p>One code for both on purpose. A stranger trying ids in turn learns nothing either way,
     * which is the whole point of answering a withheld entry as a missing one.
     */
    PUBLIC_BLOG_ENTRY_NOT_HERE(27, HttpStatus.NOT_FOUND, "That blog entry is not here"),

    /** An entry written with nothing in it, where it is not built from blocks. */
    NEWS_NEEDS_SOMETHING_WRITTEN(
            28, HttpStatus.BAD_REQUEST, "An entry needs something written in it, so nothing was saved"),

    /** An entry of the instance, published without a title. */
    SYSTEM_NEWS_NEEDS_A_TITLE(29, HttpStatus.BAD_REQUEST, Sentences.NEWS_NEEDS_A_TITLE),

    /** An entry of the instance, corrected without a title. */
    SYSTEM_NEWS_NEEDS_A_TITLE_ON_UPDATE(30, HttpStatus.BAD_REQUEST, Sentences.NEWS_NEEDS_A_TITLE),

    /** An entry of the instance, corrected after it had gone. */
    SYSTEM_NEWS_NOT_HERE_ON_UPDATE(31, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry of the instance, withdrawn after it had gone. */
    SYSTEM_NEWS_NOT_DELETED(32, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** Blocks saved against an entry of the instance that has gone. */
    SYSTEM_NEWS_NOT_HERE_ON_BLOCK_SAVE(33, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry of the instance, handed to the page editor after it had gone. */
    SYSTEM_NEWS_NOT_HERE_ON_BLOCK_SWITCH(34, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An upload into the library the instance holds, arriving with no file in it. */
    INSTANCE_UPLOAD_MISSING_FILE(35, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** An upload into the library the instance holds, larger than the instance takes. */
    INSTANCE_UPLOAD_TOO_LARGE(36, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** An upload the library refused on its own terms. */
    INSTANCE_UPLOAD_NOT_TAKEN(
            37, HttpStatus.BAD_REQUEST, "That file is not one the library takes, so nothing was saved"),

    /** An upload into the library the instance holds, which did not come through. */
    INSTANCE_UPLOAD_NOT_SAVED(38, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /**
     * A file of the instance that is not here, or one that belongs to a station instead.
     *
     * <p>One code for both on purpose. A station's file is that station's business, and saying so
     * would confirm it exists to somebody addressing the instance's own library.
     */
    INSTANCE_FILE_NOT_HERE(39, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file of the instance that went while it was being removed. */
    INSTANCE_FILE_NOT_DELETED(40, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * An entry of the instance that is not here, or one a station wrote for its own members.
     *
     * <p>One code for both on purpose. These routes answer for what the instance said and nothing
     * else, and a station's entry has to look absent rather than withheld.
     */
    SYSTEM_NEWS_NOT_HERE(41, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** An entry asked for by a partner instance, gone between the share check and the read. */
    REMOTE_NEWS_NOT_HERE(42, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** A comment from a partner instance with nothing written in it. */
    REMOTE_NEWS_COMMENT_NEEDS_TEXT(43, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment from a partner instance, changed to nothing at all. */
    REMOTE_NEWS_COMMENT_NEEDS_TEXT_ON_UPDATE(44, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment changed by a partner instance after it had gone. */
    REMOTE_NEWS_COMMENT_NOT_HERE_ON_UPDATE(45, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment of another member, changed by a partner instance. */
    REMOTE_NEWS_COMMENT_NOT_YOURS_TO_EDIT(46, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /** A comment that went while a partner instance was changing it. */
    REMOTE_NEWS_COMMENT_NOT_HERE_AFTER_UPDATE(47, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment deleted by a partner instance after it had gone. */
    REMOTE_NEWS_COMMENT_NOT_HERE_ON_DELETE(48, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment of another member, deleted by a partner instance. */
    REMOTE_NEWS_COMMENT_NOT_YOURS_TO_DELETE(49, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** A comment that went while a partner instance was deleting it. */
    REMOTE_NEWS_COMMENT_NOT_DELETED(50, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** An entry addressed by a partner instance it was never shared with. */
    NEWS_NOT_SHARED_WITH_PARTNER(51, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** A comment sent on to a partner instance with nothing written in it. */
    FEDERATED_NEWS_COMMENT_NEEDS_TEXT(52, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A news block on a page of a station that is not here. */
    STATION_NOT_HERE_BEHIND_NEWS_BLOCK(53, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A news block on a page of a station that keeps no public blog, so no entry is shown. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_NEWS_BLOCK(54, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /**
     * A news block naming an entry that is gone, belongs to another station, is not published yet,
     * is kept to part of the station or was never put on the public blog. All of them answer alike,
     * so a block cannot tell a withheld entry from a missing one.
     */
    NEWS_BLOCK_ENTRY_NOT_HERE(55, HttpStatus.NOT_FOUND, "That news entry is not available here"),

    /**
     * A page saved with a news block naming an entry that is not on the station's public blog. A page
     * is read by anybody, so nothing is saved rather than a block nobody outside can see.
     */
    NEWS_BLOCK_ENTRY_NOT_PUBLIC(
            56,
            HttpStatus.BAD_REQUEST,
            "A page can only show a news entry on the station's public blog, so nothing was saved"),

    /**
     * An article saved with a news block naming an entry that is not published, is kept to part of
     * the station or is not the station's. An article is read by every member, so it may only show
     * what every member may read.
     */
    NEWS_BLOCK_ENTRY_NOT_FOR_EVERY_MEMBER(
            57,
            HttpStatus.BAD_REQUEST,
            "An article can only show a news entry every member may read, so nothing was saved"),

    /** A file hung on a news entry that is not in the media library. */
    NEWS_ATTACHMENT_FILE_NOT_HERE(58, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file hung on a news entry that belongs to another station's media library. */
    NEWS_ATTACHMENT_FILE_ELSEWHERE(
            59, HttpStatus.BAD_REQUEST, "That file belongs to another station, so nothing was attached"),

    /** The partner whose entry is commented on, not a partner of this station any more. */
    NEWS_COMMENT_PARTNER_NOT_HERE(60, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** Blocks saved onto a news entry that is not built from them. */
    NEWS_ENTRY_NOT_BUILT_FROM_BLOCKS(
            61, HttpStatus.BAD_REQUEST, "This entry is not built from blocks, so nothing was saved");

    private final Definition definition;

    NewsRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.NEWS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
