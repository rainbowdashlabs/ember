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

    /**
     * A row of a letter, or of blocks stacked in one of its columns, of more columns than its part holds:
     * three in the text, four in the header and the footer.
     */
    DOCUMENT_TEMPLATE_TOO_MANY_CELLS(
            28,
            HttpStatus.BAD_REQUEST,
            "A row of a letter holds at most three columns in the text and four in the header and footer"),

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

    /**
     * A font or a web version of one uploaded without the confirmation that it may be used for documents
     * and shown in the template editor.
     */
    DOCUMENT_FONT_LICENCE_NOT_CONFIRMED(
            62,
            HttpStatus.BAD_REQUEST,
            "Confirm that the font may be used for these documents and shown in the template editor"),

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

    /** A station changing a template its association keeps. */
    DOCUMENT_TEMPLATE_KEPT_BY_ASSOCIATION(
            80, HttpStatus.FORBIDDEN, "This template belongs to the association and can only be changed there"),

    /** A station setting how it uses a template, where the template is its own rather than its association's. */
    DOCUMENT_TEMPLATE_USE_NOT_ASSOCIATION(
            81,
            HttpStatus.CONFLICT,
            "Only a template of the association is switched on here; the station's own templates say this themselves"),

    /** A template of an association drawn for a member at the association, which has no members of its own. */
    DOCUMENT_ASSOCIATION_PREVIEW_WITH_MEMBER(
            82, HttpStatus.BAD_REQUEST, "A template of the association is shown without a member"),

    /** A template of an association given a self service audience, which each station chooses itself. */
    DOCUMENT_ASSOCIATION_TEMPLATE_AUDIENCE(
            83,
            HttpStatus.BAD_REQUEST,
            "The association only offers a template for self service; each station chooses who may use it"),

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
            100, HttpStatus.BAD_REQUEST, "An appointment asks for a limited number of documents, so nothing was saved"),

    /** A PDF template being copied whose stored PDF cannot be read. */
    DOCUMENT_TEMPLATE_COPY_PDF_GONE(
            101, HttpStatus.CONFLICT, "The PDF of this template could not be read, so no copy was made"),

    /**
     * An issuer named for a template, for how a station uses one of its association, or for one document
     * or run, who is no current member of the station. An association names none, since it has no members.
     */
    DOCUMENT_ISSUER_NOT_HERE(102, HttpStatus.BAD_REQUEST, "The issuing person is no current member of this station"),

    /** A sample asked for of a font family the owner does not reach. */
    DOCUMENT_FONT_SAMPLE_UNKNOWN(
            110, HttpStatus.NOT_FOUND, "This font is not available here, so there is no sample of it"),

    /**
     * A template printing a date in a format that is no ready-made one and no valid own one, or in one
     * with a time of day for a date that has none.
     */
    DOCUMENT_TEMPLATE_DATE_FORMAT_INVALID(
            111, HttpStatus.BAD_REQUEST, "A date in the template has a format that cannot be printed"),

    /** A template giving a format to a placeholder that holds no date. */
    DOCUMENT_TEMPLATE_FORMAT_NOT_A_DATE(112, HttpStatus.BAD_REQUEST, "Only a date can be given a format in a template"),

    /**
     * A font file asked for by the template editor of a family the owner does not reach, of a style the
     * default font has no web file of, or of a family whose file the server does not hold.
     */
    DOCUMENT_FONT_FILE_UNKNOWN(
            120, HttpStatus.NOT_FOUND, "This font cannot be shown in the editor, so its name stands in for it"),

    /** A web version of a font style that is no web font, TrueType or OpenType file, or one that cannot be read. */
    DOCUMENT_WEB_FONT_NOT_A_FONT(
            121,
            HttpStatus.BAD_REQUEST,
            "Only WOFF2, WOFF, TrueType (.ttf) and OpenType (.otf) files can be a web version"),

    /** A template list asked for by a kind of template or an order that does not exist. */
    DOCUMENT_TEMPLATE_LIST_UNKNOWN_CHOICE(
            122, HttpStatus.BAD_REQUEST, "The templates cannot be listed by that kind or in that order"),

    /** A sealed document asked to be removed, alone or among others. */
    DOCUMENT_SEALED_NOT_REMOVABLE(123, HttpStatus.CONFLICT, "A sealed document is kept and cannot be deleted"),

    /** The members of a sealed document asked to be changed. */
    DOCUMENT_SEALED_MEMBERS_FIXED(
            124, HttpStatus.CONFLICT, "A sealed document stays with the members it was sealed for"),

    /** A sealed version offered for a document that was not filed sealed. */
    DOCUMENT_NOT_SEALED(125, HttpStatus.CONFLICT, "This document is not sealed, so it takes no sealed version"),

    /** A sealed version built on a version of the document that another one has superseded since. */
    DOCUMENT_SEALED_VERSION_OUTDATED(
            126, HttpStatus.CONFLICT, "The document has a newer sealed version since, so this one was not filed"),

    /** A signing authority's certificate or revocation list asked for by a serial number it never had. */
    SIGNING_AUTHORITY_NOT_HERE(
            127, HttpStatus.NOT_FOUND, "This installation has no signing authority with that serial number"),

    /** A station's seal certificate asked for by a serial number none of its keys had. */
    SEAL_CERTIFICATE_NOT_HERE(
            128, HttpStatus.NOT_FOUND, "This station has no seal certificate with that serial number"),

    /** The seal certificates of a station that does not exist or never sealed a document. */
    SEALING_STATION_NOT_HERE(129, HttpStatus.NOT_FOUND, "No station here seals documents under that address"),

    /** A seal check asked for without a file in the field it is read from. */
    SEAL_CHECK_NO_FILE(130, HttpStatus.BAD_REQUEST, "No file was sent to check"),

    /** A file sent for a seal check that is larger than a check takes. */
    SEAL_CHECK_TOO_LARGE(131, HttpStatus.CONTENT_TOO_LARGE, "A file to check may be at most 25 MB"),

    /** A file sent for a seal check that is not a PDF or cannot be read as one. */
    SEAL_CHECK_NOT_A_PDF(132, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file is not a PDF that can be read"),

    /** A file sent for a seal check whose upload broke off before it was complete. */
    SEAL_CHECK_NOT_RECEIVED(133, HttpStatus.BAD_REQUEST, "The file did not arrive completely, so it was not checked"),

    /** A signing act asked of an account that has no proof a signature could be confirmed with. */
    SIGNING_NO_PROOF(
            134,
            HttpStatus.FORBIDDEN,
            "This account has nothing to confirm a signature with: no passkey, security key, authenticator app or password"),

    /** A signature confirmed with a proof signing does not take, or one the account does not have. */
    SIGNING_PROOF_NOT_ACCEPTED(135, HttpStatus.FORBIDDEN, "A signature cannot be confirmed this way"),

    /** A passkey or security key answer that signed another challenge than this act's. */
    SIGNING_CHALLENGE_MISMATCH(
            136, HttpStatus.BAD_REQUEST, "The confirmation belongs to another document or another signing attempt"),

    /** A passkey or security key answer that is not the answer to a request for confirmation. */
    SIGNING_NOT_AN_ASSERTION(
            137,
            HttpStatus.BAD_REQUEST,
            "The confirmation is not the answer a passkey or security key gives when asked to confirm"),

    /** A passkey or security key answer given on another site than this installation. */
    SIGNING_FOREIGN_ORIGIN(
            138, HttpStatus.BAD_REQUEST, "The confirmation was given on another site than this installation"),

    /** A passkey or security key answer without the authenticator checking who holds it. */
    SIGNING_NOT_USER_VERIFIED(
            139,
            HttpStatus.FORBIDDEN,
            "The passkey or security key did not check who is holding it, so it cannot confirm a signature"),

    /** A passkey or security key answer that does not verify against a credential of the account. */
    SIGNING_ASSERTION_INVALID(
            140, HttpStatus.BAD_REQUEST, "The confirmation of the passkey or security key could not be verified"),

    /** A request for signatures that does not exist at this station. */
    SIGNING_REQUEST_NOT_FOUND(141, HttpStatus.NOT_FOUND, "No request for signatures exists here under that address"),

    /** A signature field the request does not ask to be signed. */
    SIGNING_FIELD_NOT_FOUND(142, HttpStatus.NOT_FOUND, "The document asks for no signature in that field"),

    /** A signature field that was already signed, confirmed, waived or withdrawn. */
    SIGNING_FIELD_NOT_OPEN(143, HttpStatus.CONFLICT, "This signature field no longer waits for a signature"),

    /** A signature for a field that is not the signer's to sign, or not in the way it was given. */
    SIGNING_FIELD_NOT_YOURS(144, HttpStatus.FORBIDDEN, "This signature field is not yours to sign"),

    /** A signature given on other content or another statement than the request asks for. */
    SIGNING_CONTENT_DIFFERS(
            145,
            HttpStatus.CONFLICT,
            "The signature was given on another document or another statement than the one asked for"),

    /** Signatures asked for on a generated document that does not exist at this station. */
    SIGNING_GENERATION_NOT_FOUND(146, HttpStatus.NOT_FOUND, "No generated document exists here under that address"),

    /** Signatures asked for on a generated document that is no longer filed. */
    SIGNING_DOCUMENT_NOT_FILED(
            147, HttpStatus.CONFLICT, "The generated document is no longer filed, so no signatures can be asked for"),

    /** Signatures asked for on a filed document whose file is no longer the generated one. */
    SIGNING_DOCUMENT_CHANGED(148, HttpStatus.CONFLICT, "The filed document is no longer the one that was generated"),

    /** Signatures asked for on a document without a signature field. */
    SIGNING_NO_FIELDS(149, HttpStatus.CONFLICT, "The document has no signature fields to sign"),

    /** Signatures asked for twice on one generated document. */
    SIGNING_ALREADY_REQUESTED(150, HttpStatus.CONFLICT, "Signatures are already asked for on this document"),

    /** Signatures asked for on a document about a member who left or was deleted. */
    SIGNING_MEMBER_GONE(
            151,
            HttpStatus.CONFLICT,
            "The member the document is about is no longer here, so no signatures can be asked for"),

    /** A request for signatures withdrawn while it no longer waits for any. */
    SIGNING_REQUEST_NOT_OPEN(152, HttpStatus.CONFLICT, "This request for signatures no longer waits for signatures"),

    /** A request for signatures corrected after it was withdrawn or already replaced. */
    SIGNING_REQUEST_ENDED(153, HttpStatus.CONFLICT, "This request for signatures was withdrawn or replaced"),

    /** A corrected document about another member than the one it is to replace. */
    SIGNING_CORRECTION_OTHER_MEMBER(
            154, HttpStatus.CONFLICT, "A corrected document must be about the same member as the one it replaces"),

    /** A signing act completed under a token that names no start of this account for this field. */
    SIGNING_START_UNKNOWN(
            155,
            HttpStatus.NOT_FOUND,
            "No signing attempt for this field waits under that token, so the signing has to start again"),

    /** A signing act completed after its start expired. */
    SIGNING_START_EXPIRED(
            156, HttpStatus.GONE, "The signing attempt took longer than five minutes, so it has to start again"),

    /** A signing act completed without the start token, the proof or the answer the proof needs. */
    SIGNING_ANSWER_MISSING(
            157, HttpStatus.BAD_REQUEST, "The confirmation came without the attempt, the proof or its answer"),

    /** A signing act confirmed with an authenticator app code that was not right. */
    SIGNING_CODE_WRONG(158, HttpStatus.FORBIDDEN, "That code was not right, so nothing was signed"),

    /** A signing act confirmed with a password that was not right. */
    SIGNING_PASSWORD_WRONG(159, HttpStatus.FORBIDDEN, "That password was not right, so nothing was signed"),

    /** Confirming signing acts with a code, a passkey or a security key far more often than a person could. */
    SIGNING_CONFIRMATION_TOO_OFTEN(160, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Confirming signing acts with the password far more often than a person could. */
    SIGNING_PASSWORD_TOO_OFTEN(161, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A signing act on a document that is no longer filed or can no longer be read. */
    SIGNING_DOCUMENT_GONE(162, HttpStatus.CONFLICT, "The document to sign is no longer filed, so it cannot be signed"),

    /** A value typed at signing without the name of its field. */
    SIGNING_ENTRY_UNNAMED(163, HttpStatus.BAD_REQUEST, "A value was filled in without naming its field"),

    /** A field to fill in at signing that was sent empty. */
    SIGNING_ENTRY_EMPTY(164, HttpStatus.BAD_REQUEST, "A field to fill in was left empty"),

    /** A field filled in twice at signing. */
    SIGNING_ENTRY_TWICE(165, HttpStatus.BAD_REQUEST, "A field was filled in twice"),

    /** A field name or value typed at signing that is longer than a field holds. */
    SIGNING_ENTRY_TOO_LONG(
            166, HttpStatus.BAD_REQUEST, "A field name may be at most 64 characters long and a value at most 500"),

    /** More fields filled in at signing than a document carries. */
    SIGNING_TOO_MANY_ENTRIES(167, HttpStatus.BAD_REQUEST, "At most 20 fields can be filled in when signing"),

    /** A seal check sent while the server is already busy with as many checks as it runs at once. */
    SEAL_CHECKS_BUSY(
            168, HttpStatus.SERVICE_UNAVAILABLE, "Too many documents are being checked right now, try again shortly"),

    /** A signing act started while the account already holds as many unfinished starts as it may. */
    SIGNING_STARTS_TOO_MANY(
            169,
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many signing attempts are open at once, finish one or wait a few minutes"),

    /** A scan of a signed copy that was not handed in for this appointment of the station. */
    DOCUMENT_SCAN_NOT_FOUND(170, HttpStatus.NOT_FOUND, "This scan was not handed in for this appointment"),

    /** A scan confirmed or turned down that was already decided. */
    DOCUMENT_SCAN_NOT_WAITING(171, HttpStatus.CONFLICT, "This scan was already confirmed or turned down"),

    /** A scan handed in for a document whose signed paper copy was already confirmed. */
    DOCUMENT_SCAN_ALREADY_CONFIRMED(
            172, HttpStatus.CONFLICT, "The signed copy of this document was already confirmed, so no scan was taken"),

    /** A scan turned down without saying why. */
    DOCUMENT_SCAN_REASON_MISSING(173, HttpStatus.BAD_REQUEST, "Say briefly why the scan is turned down"),

    /** A reason for turning a scan down that is longer than the participant is shown. */
    DOCUMENT_SCAN_REASON_TOO_LONG(174, HttpStatus.BAD_REQUEST, "The reason may be at most 300 characters long"),

    /** A recovery of the signing keys while every key in use still opens, so there is nothing to give up. */
    SIGNING_KEYS_ALL_OPEN(175, HttpStatus.CONFLICT, "Every signing key still opens, so nothing is given up"),

    /** A recovery confirmed for other keys than the ones that no longer open now. */
    SIGNING_KEYS_CHANGED(
            176,
            HttpStatus.CONFLICT,
            "The signing keys that no longer open have changed since the page was loaded, nothing was given up"),

    /** A signature picture to save that was not sent. */
    SIGNATURE_IMAGE_MISSING(177, HttpStatus.BAD_REQUEST, "No signature picture was sent"),

    /** A signature picture that is no picture this server reads. */
    SIGNATURE_IMAGE_NOT_A_PICTURE(
            178, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A signature picture has to be a PNG, JPEG or WebP picture"),

    /** A signature picture larger than one is taken. */
    SIGNATURE_IMAGE_TOO_LARGE(179, HttpStatus.CONTENT_TOO_LARGE, "A signature picture may be at most 5 MB"),

    /** A signature picture on which no signature stands out from the background. */
    SIGNATURE_IMAGE_EMPTY(180, HttpStatus.BAD_REQUEST, "No signature can be made out on the picture"),

    /** A signature picture sent without saying how it was made. */
    SIGNATURE_IMAGE_SOURCE_UNKNOWN(
            181, HttpStatus.BAD_REQUEST, "Say whether the signature was drawn, typed or uploaded"),

    /** A signing act confirmed without a signature picture, where none is saved to use. */
    SIGNING_MARK_MISSING(182, HttpStatus.BAD_REQUEST, "Draw a signature to sign with, since none is saved"),

    /** What the signer of a signature field confirms, written longer than a statement may be. */
    DOCUMENT_TEMPLATE_STATEMENT_TOO_LONG(183, HttpStatus.BAD_REQUEST, "A signature statement is too long"),

    /** A retention period for signed documents outside the months a template may keep them. */
    DOCUMENT_TEMPLATE_RETENTION_OUT_OF_RANGE(
            184, HttpStatus.BAD_REQUEST, "Signed documents are kept for 0 to 240 months after the member left"),

    /** A partner asking for the signing authorities without a challenge of 32 bytes in hexadecimal. */
    AUTHORITY_CHALLENGE_MALFORMED(
            190, HttpStatus.BAD_REQUEST, "The signing authorities are only stated against a fresh challenge"),

    /** A partner asking a station for its signing authorities that has no federation key to sign them with. */
    AUTHORITIES_CANNOT_BE_VOUCHED_FOR(
            191, HttpStatus.CONFLICT, "This station has no federation key to vouch for its signing authorities"),

    /** A scan handed in at the same moment as another one for the same document, participant and date. */
    DOCUMENT_SCAN_HANDED_IN_AT_ONCE(
            230,
            HttpStatus.CONFLICT,
            "Another scan of this document was handed in at the same moment, so this one was not filed"),

    /** A sealed version a document does not have, or any version of a document that is not sealed. */
    SEALED_VERSION_NOT_FOUND(250, HttpStatus.NOT_FOUND, "This document has no sealed version with that number"),

    /** Signatures asked for again without naming the corrected document to ask them on. */
    SIGNING_CORRECTION_NOT_NAMED(
            251, HttpStatus.BAD_REQUEST, "Name the corrected document the signatures are to be asked for on"),

    /** A field to fill in at signing without a label or without the signer who fills it in. */
    DOCUMENT_TEMPLATE_FILL_IN_INCOMPLETE(
            260, HttpStatus.BAD_REQUEST, "A field to fill in needs a label and the signer who fills it in"),

    /** The label of a field to fill in at signing, longer than a label may be. */
    DOCUMENT_TEMPLATE_FILL_IN_LABEL_TOO_LONG(
            261, HttpStatus.BAD_REQUEST, "The label of a field to fill in may be at most 100 characters long"),

    /** A maximum length of a field to fill in outside what a field holds. */
    DOCUMENT_TEMPLATE_FILL_IN_LENGTH_OUT_OF_RANGE(
            262, HttpStatus.BAD_REQUEST, "A field to fill in holds 1 to 500 characters"),

    /** A field to fill in for a signer the template has no signature field for. */
    DOCUMENT_TEMPLATE_FILL_IN_WITHOUT_SIGNATURE(
            263, HttpStatus.BAD_REQUEST, "A field to fill in needs a signature field of the same signer"),

    /** More fields to fill in for one signer than one act takes. */
    DOCUMENT_TEMPLATE_FILL_IN_TOO_MANY(264, HttpStatus.BAD_REQUEST, "One signer fills in at most 20 fields"),

    /** A value typed at signing for a field the document does not ask this signer to fill in. */
    SIGNING_ENTRY_NOT_ASKED(265, HttpStatus.BAD_REQUEST, "The document does not ask this signer to fill in that field"),

    /** A field the signer has to fill in, left out at signing. */
    SIGNING_ENTRY_REQUIRED(266, HttpStatus.BAD_REQUEST, "A field that has to be filled in was left out"),

    /** A value typed at signing that is longer than its field takes. */
    SIGNING_ENTRY_LONGER_THAN_FIELD(267, HttpStatus.BAD_REQUEST, "A value is longer than its field takes"),

    /** A field to fill in for the issuer, whose signature can be made without them filling anything in. */
    DOCUMENT_TEMPLATE_FILL_IN_FOR_ISSUER(
            268, HttpStatus.BAD_REQUEST, "A field to fill in cannot be for the member who issues the document"),

    /** A document partners sign for a shared appointment that names a person, not only the appointment and the station. */
    PARTNER_AGREEMENT_NAMES_A_PERSON(
            270,
            HttpStatus.BAD_REQUEST,
            "A document partners sign may only name the appointment and the station, never a person"),

    /** A document partners sign whose blocks depend on who it is for, so it would not read alike for everybody. */
    PARTNER_AGREEMENT_DEPENDS_ON_THE_MEMBER(
            271,
            HttpStatus.BAD_REQUEST,
            "A document partners sign has to read the same for everybody, without blocks for some only"),

    /** A document partners sign that asks a signer whose fields depend on the member, or the issuer. */
    PARTNER_AGREEMENT_SIGNER_NOT_SHARED(
            272,
            HttpStatus.BAD_REQUEST,
            "A document partners sign may only ask the participant, the first guardian or any guardian to sign"),

    /** A paper copy confirmed for a partner's member who does not take part on that date. */
    PARTNER_AGREEMENT_REGISTRATION_NOT_HERE(
            273, HttpStatus.NOT_FOUND, "This partner's member is not registered for the appointment on that day"),

    /** A paper copy confirmed for a document the appointment does not ask partners to sign. */
    PARTNER_AGREEMENT_NOT_ASKED(
            274, HttpStatus.NOT_FOUND, "The appointment does not ask partners to sign this document"),

    /** A paper copy confirmed for a partner's member whose signed copy already came back. */
    PARTNER_AGREEMENT_ALREADY_SIGNED(
            275, HttpStatus.CONFLICT, "The signed copy of this document already came back from the partner"),

    /** The signed copy of a partner's agreement asked for where none came back. */
    PARTNER_AGREEMENT_COPY_NOT_HERE(276, HttpStatus.NOT_FOUND, "No signed copy of this document came back yet"),

    /** A signed agreement withdrawn by somebody who neither acts for its member nor signed it. */
    SIGNATURE_WITHDRAWAL_NOT_YOURS(290, HttpStatus.FORBIDDEN, "This agreement is not yours to withdraw"),

    /** A signed agreement withdrawn after it was withdrawn already, replaced or never agreed to. */
    SIGNATURE_WITHDRAWAL_ENDED(291, HttpStatus.CONFLICT, "This agreement no longer stands"),

    /** An agreement withdrawn online that nobody signed online, such as one confirmed on paper only. */
    SIGNATURE_WITHDRAWAL_NOTHING_SIGNED(
            292, HttpStatus.CONFLICT, "Nothing was signed online on this agreement, so it is withdrawn on paper"),

    /** The reason for a withdrawal written longer than it may be. */
    SIGNATURE_WITHDRAWAL_REASON_TOO_LONG(293, HttpStatus.BAD_REQUEST, "A reason may be at most 500 characters"),

    /** Signing in one go started without a single field to sign. */
    SIGNING_BATCH_EMPTY(300, HttpStatus.BAD_REQUEST, "Choose at least one field to sign"),

    /** More fields signed in one go than one confirmation takes. */
    SIGNING_BATCH_TOO_LARGE(301, HttpStatus.BAD_REQUEST, "At most 50 fields are signed in one go"),

    /** The same field chosen twice for signing in one go. */
    SIGNING_BATCH_FIELD_TWICE(302, HttpStatus.BAD_REQUEST, "A field was chosen twice"),

    /** A signature picture sent for a person who signs none of the fields of the confirmation. */
    SIGNING_PICTURE_FOR_NOBODY(
            303, HttpStatus.BAD_REQUEST, "A signature picture was sent for somebody who signs nothing here"),

    /** Two signature pictures sent for one person in one confirmation. */
    SIGNING_PICTURE_TWICE(304, HttpStatus.BAD_REQUEST, "Two signature pictures were sent for one person"),

    /** The signature record asked for a sealed version that carries no evidence of signatures. */
    SIGNATURE_RECORD_NONE(305, HttpStatus.NOT_FOUND, "This version carries no record of signatures");

    private final Definition definition;

    DocumentRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.DOCUMENTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
