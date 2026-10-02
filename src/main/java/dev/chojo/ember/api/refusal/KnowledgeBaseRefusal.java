/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#KNOWLEDGE_BASE}: the knowledge base.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum KnowledgeBaseRefusal implements Refusal {
    /**
     * A folder or an article of the wiki that the reader's reach does not cover. Answered as though
     * it were not there, because saying it is there and withheld is already saying it is there.
     */
    KB_ENTRY_NOT_YOURS_TO_OPEN(1, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An upload to the wiki that arrived without the file itself. */
    KB_UPLOAD_MISSING_FILE(2, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A file bigger than the wiki takes. */
    KB_UPLOAD_TOO_LARGE(3, HttpStatus.BAD_REQUEST, "That file is larger than the wiki accepts"),

    /** A folder being made with nothing in the name box. */
    KB_FOLDER_NEEDS_A_NAME(4, HttpStatus.BAD_REQUEST, "A folder needs a name, so nothing was saved"),

    /** A folder that went before the change to it could be written. */
    KB_FOLDER_NOT_CHANGED(5, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A folder that went between being changed and being read back. */
    KB_FOLDER_NOT_HERE_AFTER_CHANGE(6, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A folder that was already gone when it was to be put in the trash. */
    KB_FOLDER_NOT_TRASHED(7, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** The folder a move aims at, which is not one the reader may put anything into. */
    KB_MOVE_TARGET_NOT_USABLE(8, HttpStatus.NOT_FOUND, "Nothing can be put into that folder, so nothing was moved"),

    /** A move being looked ahead at without saying what is to be moved. */
    KB_MOVE_PREVIEW_NEEDS_AN_ENTRY(9, HttpStatus.BAD_REQUEST, "Say which folder or article the move is for"),

    /** A folder being thrown away for good that is no longer in the trash. */
    KB_FOLDER_NOT_PURGED(10, HttpStatus.NOT_FOUND, "That folder is not in the trash any more"),

    /** An article being thrown away for good that is no longer in the trash. */
    KB_ARTICLE_NOT_PURGED(11, HttpStatus.NOT_FOUND, "That article is not in the trash any more"),

    /** An article that went before the change to its details could be written. */
    KB_ARTICLE_NOT_CHANGED(12, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An article that went between being changed and being read back. */
    KB_ARTICLE_NOT_HERE_AFTER_CHANGE(13, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An article that was already gone when it was to be put in the trash. */
    KB_ARTICLE_NOT_TRASHED(14, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** A written article being made with nothing in the name box. */
    KB_ARTICLE_NEEDS_A_NAME(15, HttpStatus.BAD_REQUEST, Sentences.KB_ARTICLE_NEEDS_A_NAME),

    /** A video entry being made with nothing in the name box. */
    KB_VIDEO_NEEDS_A_NAME(16, HttpStatus.BAD_REQUEST, Sentences.KB_ARTICLE_NEEDS_A_NAME),

    /** A video entry being made without the address of the video. */
    KB_VIDEO_NEEDS_AN_ADDRESS(
            17, HttpStatus.BAD_REQUEST, "A video entry needs the address of the video, so nothing was saved"),

    /** A link entry being made without the address it is to lead to. */
    KB_LINK_NEEDS_AN_ADDRESS(18, HttpStatus.BAD_REQUEST, "A link entry needs an address, so nothing was saved"),

    /** An upload whose contents could not be read off the request at all. */
    KB_UPLOAD_NOT_READ(19, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was saved"),

    /** A document offered for reading in that is of a kind the wiki cannot take apart. */
    KB_IMPORT_KIND_UNKNOWN(
            20, HttpStatus.BAD_REQUEST, "That kind of document cannot be read in here, so nothing was saved"),

    /** A document that was of a readable kind and still could not be turned into an article. */
    KB_IMPORT_FAILED(
            21, HttpStatus.BAD_REQUEST, "That document could not be turned into an article, so nothing was saved"),

    /** A written article whose text is gone. */
    KB_ARTICLE_TEXT_NOT_HERE(22, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_HAS_NOTHING_TO_SHOW),

    /** An uploaded entry whose stored file is gone. */
    KB_FILE_CONTENT_NOT_HERE(23, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A presentation asked for before it had been turned into something a browser can show. */
    KB_PRESENTATION_NOT_READY(24, HttpStatus.NOT_FOUND, "That presentation is still being prepared. Try again shortly"),

    /** A written article whose text is gone, asked for as a drawn page. */
    KB_ARTICLE_TEXT_NOT_HERE_AS_PAGE(25, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_HAS_NOTHING_TO_SHOW),

    /** An article that went before its blocks could be written. */
    KB_BLOCKS_NOT_SAVED(26, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An article that went before it could be turned into one built from blocks. */
    KB_BLOCKS_NOT_ENABLED(27, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An entry asked for as a PDF that holds no text to set. */
    KB_NOT_A_PDF_TO_MAKE(28, HttpStatus.BAD_REQUEST, Sentences.KB_ONLY_WRITTEN_AS_PDF),

    /** A PDF whose setting was stopped before it finished. */
    KB_PDF_STOPPED(29, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A PDF that broke while it was being set, which is Ember's to look into. */
    KB_PDF_NOT_MADE(30, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** The file an entry was made from, asked for on an entry that was not made from one. */
    KB_ORIGINAL_NOT_KEPT(31, HttpStatus.BAD_REQUEST, Sentences.KB_ORIGINAL_ONLY_FOR_PRESENTATIONS),

    /** A presentation whose stored file is gone. */
    KB_ORIGINAL_NOT_HERE(32, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A replacement file sent for an entry that was not made from one. */
    KB_ORIGINAL_NOT_REPLACEABLE(33, HttpStatus.BAD_REQUEST, Sentences.KB_ORIGINAL_ONLY_FOR_PRESENTATIONS),

    /** A presentation that broke while it was being replaced. */
    KB_PRESENTATION_NOT_REPLACED(
            34,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "That presentation could not be replaced, so nothing was saved. Trying again may work"),

    /** An earlier state of an article that is gone. */
    KB_VERSION_NOT_HERE(35, HttpStatus.NOT_FOUND, "That version of the article is not here any more"),

    /** An article whose tile picture could not be had. */
    KB_ARTICLE_PICTURE_NOT_HERE(36, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A folder icon sent without the picture itself. */
    KB_FOLDER_ICON_MISSING(37, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A folder icon of a kind the wiki does not take. */
    KB_FOLDER_ICON_KIND_NOT_TAKEN(38, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A folder icon that was read and then refused for what it held. */
    KB_FOLDER_ICON_NOT_SAVED(39, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** A folder icon that broke on the way in, which is Ember's to look into. */
    KB_FOLDER_ICON_NOT_PROCESSED(40, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A picture for an article sent without the picture itself. */
    KB_ARTICLE_IMAGE_MISSING(41, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture for an article of a kind the wiki does not take. */
    KB_ARTICLE_IMAGE_KIND_NOT_TAKEN(42, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A picture for an article that was read and then refused for what it held. */
    KB_ARTICLE_IMAGE_NOT_SAVED(43, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** A picture for an article that broke on the way in, which is Ember's to look into. */
    KB_ARTICLE_IMAGE_NOT_PROCESSED(44, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A comment on an article of this station with nothing written in it. */
    KB_COMMENT_EMPTY(45, HttpStatus.BAD_REQUEST, Sentences.KB_COMMENT_EMPTY),

    /** Somebody else's comment, being reworded. */
    KB_COMMENT_NOT_YOURS_TO_CHANGE(46, HttpStatus.FORBIDDEN, "You may only change your own comments"),

    /** A comment that went between being reworded and being read back. */
    KB_COMMENT_NOT_HERE_AFTER_CHANGE(47, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** Somebody else's comment, being deleted by a reader who does not look after the wiki. */
    KB_COMMENT_NOT_YOURS_TO_DELETE(48, HttpStatus.FORBIDDEN, "You may only delete your own comments"),

    /** A comment that was already gone when its deletion was asked for. */
    KB_COMMENT_NOT_DELETED(49, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A comment that is gone, asked for by a route that then checks whose article it hangs on. */
    KB_COMMENT_NOT_HERE(50, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A favourite being marked without saying what kind of thing it is. */
    KB_FAVOURITE_NEEDS_A_TARGET(
            52, HttpStatus.BAD_REQUEST, "Nothing was named to mark as a favourite, so nothing was saved"),

    /** A favourite at a partner station, marked without saying which partner. */
    KB_FAVOURITE_NEEDS_A_PARTNER(
            53,
            HttpStatus.BAD_REQUEST,
            "A favourite at a partner station needs the station named, so nothing was saved"),

    /** A favourite that was already gone when its mark was to be taken off. */
    KB_FAVOURITE_NOT_HERE(54, HttpStatus.NOT_FOUND, "That favourite is not here any more"),

    /** A public wiki address whose station part is not an identifier at all. */
    PUBLIC_KB_ADDRESS_NOT_A_STATION(55, HttpStatus.BAD_REQUEST, "That address does not name a station"),

    /** A station that is not here, asked for by a public wiki address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_KB(56, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A station whose public wiki is switched off, and a station whose wiki is switched off
     * altogether. One code deliberately: the reader is not signed in here and is owed nothing about
     * which of the two switches stands where.
     */
    PUBLIC_KB_SWITCHED_OFF(57, HttpStatus.NOT_FOUND, "This station has no public wiki"),

    /**
     * A folder or an article of a public wiki that is gone, belongs to another station, or was
     * never published. One code deliberately: telling them apart would say that the entry is there
     * and held back, to a reader who is not signed in to anything.
     */
    PUBLIC_KB_ENTRY_NOT_HERE(58, HttpStatus.NOT_FOUND, "That is not on this station's public wiki"),

    /** A published entry whose stored file is gone. */
    PUBLIC_KB_FILE_CONTENT_NOT_HERE(59, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A published article whose tile picture could not be had. */
    PUBLIC_KB_ARTICLE_PICTURE_NOT_HERE(60, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A published entry asked for as a drawn page that is not a written article. */
    PUBLIC_KB_NOT_A_WRITTEN_ARTICLE(61, HttpStatus.BAD_REQUEST, "That entry is not a written article"),

    /** A published entry asked for as a PDF that holds no text to set. */
    PUBLIC_KB_NOT_A_PDF_TO_MAKE(62, HttpStatus.BAD_REQUEST, Sentences.KB_ONLY_WRITTEN_AS_PDF),

    /** A PDF of a published article whose setting was stopped before it finished. */
    PUBLIC_KB_PDF_STOPPED(63, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A PDF of a published article that broke while it was being set. */
    PUBLIC_KB_PDF_NOT_MADE(64, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A comment on a partner station's article with nothing written in it. */
    PARTNER_KB_COMMENT_EMPTY(65, HttpStatus.BAD_REQUEST, Sentences.KB_COMMENT_EMPTY),

    /** A PDF of a partner station's article whose setting was stopped before it finished. */
    PARTNER_KB_PDF_STOPPED(66, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A PDF of a partner station's article that broke while it was being set. */
    PARTNER_KB_PDF_NOT_MADE(67, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A comment at a partner station that was already gone when its deletion was asked for. */
    PARTNER_KB_COMMENT_NOT_DELETED(68, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A comment sent by a partner instance with nothing written in it. */
    REMOTE_KB_COMMENT_EMPTY(69, HttpStatus.BAD_REQUEST, Sentences.KB_COMMENT_EMPTY),

    /** A comment a partner instance asked to delete that was already gone. */
    REMOTE_KB_COMMENT_NOT_DELETED(70, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A presentation that could not be read back after the file behind it was replaced. */
    KB_FILE_NOT_HERE_AFTER_REUPLOAD(71, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** An upload or a file of another kind turned into an article built from blocks. */
    KB_ONLY_WRITTEN_ARTICLES_TAKE_BLOCKS(
            72, HttpStatus.BAD_REQUEST, "Only a written article can be built from blocks, so nothing was changed"),

    /** Blocks saved onto an article that is not built from them. */
    KB_ARTICLE_NOT_BUILT_FROM_BLOCKS(
            73, HttpStatus.BAD_REQUEST, "This article is not built from blocks, so nothing was saved"),

    /**
     * An entry of this station marked as a favourite that is not here, belongs to another station,
     * or is closed to the reader. One code for all three, as the wiki gives one answer for them.
     */
    KB_FAVOURITE_ENTRY_NOT_HERE_OR_NOT_YOURS(74, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** A favourite of this station's entry that could not be read back after being marked. */
    KB_FAVOURITE_NOT_READ_BACK_AFTER_MARKING(
            75, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A partner favourite asked for with a kind of entry that does not live at a partner station. */
    KB_FAVOURITE_TARGET_NOT_AT_A_PARTNER(76, HttpStatus.NOT_FOUND, "That is not an entry at a partner station"),

    /** A favourite of a partner's entry that could not be read back after being marked. */
    KB_PARTNER_FAVOURITE_NOT_READ_BACK_AFTER_MARKING(
            77, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A partner folder marked as a favourite that the partner's answer does not name. */
    PARTNER_KB_FOLDER_NOT_IN_ITS_TRAIL(78, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** The partner a favourite is kept for, not a partner of this station any more. */
    KB_FAVOURITE_PARTNER_NOT_HERE(79, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** The partner whose article is commented on, not a partner of this station any more. */
    KB_COMMENT_PARTNER_NOT_HERE(80, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** A comment a partner's member changes or removes on an article here, which is not here. */
    REMOTE_KB_COMMENT_NOT_HERE(81, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A comment on an article here that a partner's member removes without having written it. */
    REMOTE_KB_COMMENT_NOT_YOURS_TO_DELETE(82, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** A comment on an article here that a partner's member changes without having written it. */
    REMOTE_KB_COMMENT_NOT_YOURS_TO_EDIT(83, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /**
     * An article a partner asks for that is not here, belongs to another station, or is not shared
     * with it. One code for all three: telling them apart would let a partner count the articles it
     * was never shown.
     */
    REMOTE_KB_FILE_NOT_SHARED(84, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * A folder a partner opens that is not here, belongs to another station, or is not shared with
     * it. One code for all three, for the same reason.
     */
    REMOTE_KB_FOLDER_NOT_SHARED(85, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** Who an entry is shared with, said for both an article and a folder at once, or for neither. */
    KB_AUDIENCE_NEEDS_ONE_ENTRY(86, HttpStatus.BAD_REQUEST, "Name either an article or a folder, so nothing was saved"),

    /** An entry shared with every partner while the folder above it reaches named stations only. */
    KB_SHARE_WIDER_THAN_ITS_FOLDER(
            87,
            HttpStatus.BAD_REQUEST,
            "The folder above this is shared with named stations only, so nothing was saved"),

    /** An entry shared with a station the folder above it does not reach. */
    KB_SHARE_NAMES_STATIONS_ITS_FOLDER_DOES_NOT(
            88,
            HttpStatus.BAD_REQUEST,
            "The folder above this does not reach every station named, so nothing was saved"),

    /** A partner's file asked for as a PDF that has no written body to print. */
    PARTNER_KB_ONLY_WRITTEN_AS_PDF(89, HttpStatus.BAD_REQUEST, Sentences.KB_ONLY_WRITTEN_AS_PDF);

    private final Definition definition;

    KnowledgeBaseRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.KNOWLEDGE_BASE, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
