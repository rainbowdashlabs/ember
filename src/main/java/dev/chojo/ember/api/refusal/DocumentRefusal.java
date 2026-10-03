/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#DOCUMENTS}: documents.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum DocumentRefusal implements Refusal {
    /** Documents at all, at a station that has switched them off. */
    DOCUMENTS_SWITCHED_OFF(1, HttpStatus.NOT_FOUND, "This station does not keep documents"),

    /** The documents of a member the reader neither manages nor answers for. */
    DOCUMENT_LIST_NOT_YOURS(2, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A hidden document being written by somebody who may not hide things. */
    DOCUMENT_HIDING_NOT_ALLOWED(3, HttpStatus.FORBIDDEN, "You may not mark a document as hidden"),

    /** An upload naming members, by somebody who may not file documents against one. */
    DOCUMENT_MEMBERS_NOT_YOURS_TO_NAME(5, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A document whose stored file is gone. */
    DOCUMENT_CONTENT_NOT_HERE(6, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document whose tile picture could not be had. */
    DOCUMENT_THUMBNAIL_NOT_HERE(7, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A document marked hidden, read by somebody who may not see hidden ones. */
    DOCUMENT_HIDDEN_FROM_YOU(8, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document of somebody the reader does not answer for. */
    DOCUMENT_NOT_YOURS_TO_READ(9, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A change to a document by somebody who may read it but not write it. */
    DOCUMENT_NOT_YOURS_TO_CHANGE(10, HttpStatus.FORBIDDEN, "You may not change this document"),

    /** A document filed against a member by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_ADD(11, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A mailbox rule filing attachments against members, written by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_FILE(12, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** An upload to the document store that arrived with no file in it. */
    DOCUMENT_UPLOAD_MISSING_FILE(13, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A document heavier than the store takes. */
    DOCUMENT_UPLOAD_TOO_LARGE(14, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** An upload whose bytes could not be taken in. */
    DOCUMENT_UPLOAD_UNREADABLE(17, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was filed"),

    /** An upload the station has no room left for. */
    DOCUMENT_UPLOAD_NO_ROOM(
            18, HttpStatus.BAD_REQUEST, "The station has no room left for that file, so nothing was filed"),

    /** An upload called one kind of file and made of another. */
    DOCUMENT_UPLOAD_NOT_WHAT_IT_IS_CALLED(
            19,
            HttpStatus.BAD_REQUEST,
            "That file is called one kind of file and made of another, so nothing was filed"),

    /** Tags or keeping past the membership, set by somebody who may only put a document on themselves. */
    DOCUMENT_LABELS_NOT_YOURS_TO_SET(
            20, HttpStatus.FORBIDDEN, "You may not tag a document or keep it past the membership"),

    /** A list of members that is not a list of numbers. */
    DOCUMENT_MEMBERS_NOT_NUMBERS(21, HttpStatus.BAD_REQUEST, "The members were not given as numbers"),

    /** A document template that does not exist at the reader's station. */
    DOCUMENT_TEMPLATE_NOT_HERE(22, HttpStatus.NOT_FOUND, "There is no such document template"),

    /** A document template written without a name. */
    DOCUMENT_TEMPLATE_NAME_MISSING(23, HttpStatus.BAD_REQUEST, "A document template needs a name"),

    /** A document template named like another one in use at the station. */
    DOCUMENT_TEMPLATE_NAME_TAKEN(24, HttpStatus.CONFLICT, "Another document template already has that name"),

    /** A document asked of a template that was archived. */
    DOCUMENT_TEMPLATE_ARCHIVED(25, HttpStatus.CONFLICT, "This document template is archived and generates nothing"),

    /** A template naming a placeholder the catalogue does not know. */
    DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN(
            26, HttpStatus.BAD_REQUEST, "The template uses a placeholder that does not exist"),

    /** A legal template, or a document of one, using the name a member is called by. */
    DOCUMENT_TEMPLATE_CALLED_NAME_IN_LEGAL(
            27,
            HttpStatus.BAD_REQUEST,
            "A legal document uses official names only, not the name a member is called by"),

    /** A row of a letter, or of blocks stacked in one of its columns, of more than three columns. */
    DOCUMENT_TEMPLATE_TOO_MANY_CELLS(28, HttpStatus.BAD_REQUEST, "A row of a letter holds at most three columns"),

    /** A picture block of a letter that is neither the station logo nor an image in the media library. */
    DOCUMENT_TEMPLATE_PICTURE_NOT_HERE(
            29, HttpStatus.BAD_REQUEST, "A picture of the letter is not an image in the media library"),

    /** Page margins or a text size outside what a letter may have. */
    DOCUMENT_TEMPLATE_PAGE_OUT_OF_BOUNDS(
            30,
            HttpStatus.BAD_REQUEST,
            "Margins lie between 5 and 80 millimetres and the text size between 8 and 16 points"),

    /** A self service wait below zero days. */
    DOCUMENT_TEMPLATE_COOLDOWN_NEGATIVE(
            32, HttpStatus.BAD_REQUEST, "The wait between two documents cannot be negative"),

    /** A text of a template longer than a template takes. */
    DOCUMENT_TEMPLATE_TEXT_TOO_LONG(33, HttpStatus.BAD_REQUEST, "A text of the template is too long"),

    /** A document Typst could not produce. */
    DOCUMENT_RENDER_FAILED(34, HttpStatus.INTERNAL_SERVER_ERROR, "The document could not be produced"),

    /** A document generated for somebody the reader may not file documents for. */
    DOCUMENT_GENERATE_NOT_YOURS(35, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A template members may not generate for themselves, or not this member. */
    DOCUMENT_SELF_SERVICE_NOT_OFFERED(
            36, HttpStatus.FORBIDDEN, "This document cannot be generated through self service for this member"),

    /** A self service document whose data is not complete. */
    DOCUMENT_SELF_SERVICE_VALUES_MISSING(
            37, HttpStatus.BAD_REQUEST, "Some data this document needs is missing in the profile"),

    /** A self service document asked for again before the template's wait is over. */
    DOCUMENT_SELF_SERVICE_COOLING_DOWN(
            38, HttpStatus.TOO_MANY_REQUESTS, "This document was generated recently and can be generated again from"),

    /** A self service document for somebody who is neither the reader nor in their care. */
    DOCUMENT_SELF_SERVICE_NOT_YOURS(
            39, HttpStatus.FORBIDDEN, "Documents can only be generated for yourself and the members in your care"),

    /** An import of something that is neither a Word nor an OpenDocument text. */
    DOCUMENT_IMPORT_KIND_NOT_TAKEN(
            40, HttpStatus.BAD_REQUEST, "Only Word (.docx) and OpenDocument (.odt) texts can be imported"),

    /** A text of a kind that is taken and still could not be read. */
    DOCUMENT_IMPORT_UNREADABLE(41, HttpStatus.BAD_REQUEST, "The document could not be read, so nothing was imported"),

    /** A file for a PDF template that is not a PDF. */
    DOCUMENT_TEMPLATE_PDF_NOT_A_PDF(42, HttpStatus.BAD_REQUEST, "Only a PDF can be uploaded for a PDF template"),

    /** A PDF for a template that could not be opened. */
    DOCUMENT_TEMPLATE_PDF_UNREADABLE(
            43, HttpStatus.BAD_REQUEST, "The PDF could not be read, so it was not taken as a template"),

    /** A PDF for a template that opens only with a password. */
    DOCUMENT_TEMPLATE_PDF_PASSWORD(
            44, HttpStatus.BAD_REQUEST, "The PDF opens only with a password, so it cannot be used as a template"),

    /** A PDF for a template whose protection could not be taken off. */
    DOCUMENT_TEMPLATE_PDF_PROTECTED(
            45,
            HttpStatus.BAD_REQUEST,
            "The PDF is protected in a way that cannot be removed, so it cannot be used as a template"),

    /** A PDF or fields for a template that is a letter. */
    DOCUMENT_TEMPLATE_NOT_PDF(46, HttpStatus.CONFLICT, "This template is a letter, not a PDF template"),

    /** A document asked of a PDF template no PDF was uploaded for. */
    DOCUMENT_TEMPLATE_PDF_MISSING(47, HttpStatus.CONFLICT, "No PDF has been uploaded for this template yet"),

    /** A field that does not lie on a page of the PDF. */
    DOCUMENT_TEMPLATE_FIELD_OFF_PAGE(48, HttpStatus.BAD_REQUEST, "A field lies outside the pages of the PDF"),

    /** A text or check field without its text, or a signature field without its signer. */
    DOCUMENT_TEMPLATE_FIELD_INCOMPLETE(
            49, HttpStatus.BAD_REQUEST, "A text or check field needs its text and a signature field needs its signer"),

    /** A field text size outside what a field may have. */
    DOCUMENT_TEMPLATE_FIELD_SIZE_OUT_OF_BOUNDS(
            50, HttpStatus.BAD_REQUEST, "The text size of a field lies between 4 and 72 points"),

    /** Two signature fields for the same signer that would both stand in the document of every member. */
    DOCUMENT_TEMPLATE_SIGNER_TWICE(51, HttpStatus.BAD_REQUEST, "Each signer has at most one signature field"),

    /** A form field the template fills that the PDF does not have, or that cannot be filled. */
    DOCUMENT_TEMPLATE_FORM_FIELD_UNKNOWN(
            52,
            HttpStatus.BAD_REQUEST,
            "The template fills a form field the PDF does not have or that cannot be filled"),

    /** More fields than a template holds. */
    DOCUMENT_TEMPLATE_TOO_MANY_FIELDS(53, HttpStatus.BAD_REQUEST, "A template holds at most 200 fields"),

    /** A block a letter does not print, such as a video or a map. */
    DOCUMENT_TEMPLATE_BLOCK_NOT_TAKEN(
            55,
            HttpStatus.BAD_REQUEST,
            "A letter holds texts, pictures, lines, gaps, signature lines and blocks stacked in a column only"),

    /** A font upload that arrived with no file in it. */
    DOCUMENT_FONT_MISSING_FILE(56, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A font file heavier than a font may be. */
    DOCUMENT_FONT_TOO_LARGE(57, HttpStatus.BAD_REQUEST, "A font file may be at most 10 MB"),

    /** A file that is no TrueType or OpenType font, or one that cannot be read. */
    DOCUMENT_FONT_NOT_A_FONT(
            58, HttpStatus.BAD_REQUEST, "Only TrueType (.ttf) and OpenType (.otf) fonts can be uploaded"),

    /** A font whose licence bits forbid embedding it in a document. */
    DOCUMENT_FONT_EMBEDDING_FORBIDDEN(
            59, HttpStatus.BAD_REQUEST, "The licence of this font does not allow embedding it in documents"),

    /** A second font for a family and style the owner already has. */
    DOCUMENT_FONT_TAKEN(60, HttpStatus.CONFLICT, "This family already has a font in that style"),

    /** A font uploaded without a family name, or with one too long. */
    DOCUMENT_FONT_FAMILY_INVALID(61, HttpStatus.BAD_REQUEST, "A font needs a family name of at most 60 characters"),

    /** A font uploaded without the confirmation that it may be used. */
    DOCUMENT_FONT_LICENCE_NOT_CONFIRMED(
            62, HttpStatus.BAD_REQUEST, "Confirm that the font may be used for these documents"),

    /** A font that does not exist at the owner asking for it. */
    DOCUMENT_FONT_NOT_HERE(63, HttpStatus.NOT_FOUND, "There is no such font"),

    /** A font a template in use still prints with. */
    DOCUMENT_FONT_IN_USE(64, HttpStatus.CONFLICT, "The font is used by these templates and cannot be deleted"),

    /** A font the owner has no room left for. */
    DOCUMENT_FONT_NO_ROOM(65, HttpStatus.BAD_REQUEST, "There is no room left for that font, so it was not uploaded"),

    /** A template naming a font family it cannot reach. */
    DOCUMENT_TEMPLATE_FONT_UNKNOWN(66, HttpStatus.BAD_REQUEST, "The template uses a font that is not available"),

    /** A font uploaded as a style there is none of. */
    DOCUMENT_FONT_STYLE_UNKNOWN(67, HttpStatus.BAD_REQUEST, "A font is regular, bold, italic or bold italic"),

    /** A signature block in the header or the footer of a letter. */
    DOCUMENT_TEMPLATE_SIGNATURE_OUTSIDE_BODY(
            70, HttpStatus.BAD_REQUEST, "A signature line can only stand in the text of a letter"),

    /** A signature block that does not say who signs on it. */
    DOCUMENT_TEMPLATE_SIGNER_MISSING(71, HttpStatus.BAD_REQUEST, "A signature line needs the person who signs on it"),

    /** Two signature blocks of a letter that ask the same person to sign in the document of one member. */
    DOCUMENT_SIGNER_TWICE_FOR_MEMBER(
            72,
            HttpStatus.CONFLICT,
            "For this member, two signature lines of the template ask the same person to sign, so nothing was produced"),

    /** A generation run that does not exist or is another station's. */
    DOCUMENT_JOB_NOT_HERE(90, HttpStatus.NOT_FOUND, "There is no such generation run"),

    /** A member of a generation run whose data is incomplete, where the run was not to file gaps. */
    DOCUMENT_JOB_VALUES_MISSING(
            91, HttpStatus.CONFLICT, "Data the template needs is missing for this member, so no document was filed"),

    /** A generation run that names nobody. */
    DOCUMENT_JOB_NO_MEMBERS(92, HttpStatus.BAD_REQUEST, "No member was chosen, so nothing was generated"),

    /** A generation run for more members than one run takes. */
    DOCUMENT_JOB_TOO_MANY_MEMBERS(
            93, HttpStatus.BAD_REQUEST, "One run generates for a limited number of members, so nothing was started"),

    /** A generation run naming somebody who is no current member of the station. */
    DOCUMENT_JOB_MEMBER_NOT_HERE(
            94,
            HttpStatus.BAD_REQUEST,
            "A chosen member is not a current member of this station, so nothing was started"),

    /** A template named as a document to bring that is not meant for appointments. */
    DOCUMENT_TEMPLATE_NOT_FOR_APPOINTMENTS(
            95, HttpStatus.BAD_REQUEST, "Only a template for appointments can be a document to bring"),

    /** A template that names the values of an appointment without being meant for appointments. */
    DOCUMENT_TEMPLATE_APPOINTMENT_VALUES_OUTSIDE(
            96, HttpStatus.BAD_REQUEST, "Only a template for appointments can use the values of an appointment"),

    /** A template appointments still require, which was to stop being one for appointments. */
    DOCUMENT_TEMPLATE_REQUIRED_BY_APPOINTMENTS(
            97,
            HttpStatus.CONFLICT,
            "Appointments still require this template, so it stays a template for appointments"),

    /** A document to bring asked for by somebody who is not a registered participant nor their guardian. */
    DOCUMENT_REQUIREMENT_NOT_YOURS(
            98, HttpStatus.FORBIDDEN, "Only a registered participant or their guardian gets this document"),

    /** A template the appointment does not name as a document to bring. */
    DOCUMENT_REQUIREMENT_NOT_REQUIRED(99, HttpStatus.NOT_FOUND, "The appointment does not ask for this document"),

    /** More documents to bring than an appointment may name. */
    DOCUMENT_REQUIREMENTS_TOO_MANY(
            100, HttpStatus.BAD_REQUEST, "An appointment asks for a limited number of documents, so nothing was saved");

    private final Definition definition;

    DocumentRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.DOCUMENTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
