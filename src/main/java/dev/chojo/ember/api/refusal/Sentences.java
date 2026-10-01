/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

/**
 * The sentences more than one refusal says.
 *
 * <p>Several sites refuse for the same reason in different places, and each needs its own code
 * while saying the same thing. Written out at each constant the wording drifts: one of them is
 * reworded, the others are not, and the same failure reads two ways. Named here it cannot.
 */
final class Sentences {
    static final String STATION_NOT_HERE = "That station is not here";
    static final String NOT_HERE_OR_NOT_YOURS = "That is not here any more, or it is not yours to open";
    static final String NO_CLUSTER_CHOSEN = "Choose a cluster before doing this";
    static final String CLUSTER_NOT_HERE = "That cluster is not here any more";
    static final String STATION_NOT_AN_IDENTITY = "That does not name a station";
    static final String CLUSTER_NOT_AN_IDENTITY = "That does not name a cluster";
    static final String UNEXPECTED_FAULT =
            "Something went wrong in Ember and the request was not carried out, so nothing was saved. "
                    + "Trying again may work; if it keeps happening, please report it";
    static final String CHANGE_SAVED_BUT_NOT_READ_BACK =
            "The change was saved, but it could not be read back. Reload the page to see it as it stands";
    static final String NO_STATION_CHOSEN = "Choose a station before doing this";
    static final String STATION_NEEDS_A_NAME = "A station needs a name, so nothing was saved";
    static final String STATION_NOT_FOUND_IN_DISCOVERY = "That station is not one that can be found here";
    static final String PEER_ADDRESS_MISSING = "Give the address of the instance";
    static final String PEER_NOT_HERE = "That instance is not here any more";
    static final String TOO_MANY_ATTEMPTS = "Too many attempts from here. Try again shortly";
    static final String LOCALE_NOT_GOOD = "That does not name a language this instance keeps text in";
    static final String TRACKED_TABLE_NOT_HERE = "Data tracking keeps no record of that, so nothing was changed";
    static final String STORAGE_MOVE_NOT_DONE =
            "The files could not be carried over, so nothing was changed and they are still where they were. "
                    + "The reason is in the instance log";
    static final String TRAFFIC_SPAN_MISSING = "Say which stretch of time to count over";
    static final String TRAFFIC_SPAN_NOT_A_TIME = "Each end of the stretch of time has to be a date and a time";
    static final String TRAFFIC_KIND_UNKNOWN =
            "Ask for signed-in, signed-out or federated requests, or for all of them together";
    static final String TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS = "The stretch of time cannot end before it starts";
    static final String MAP_TILE_NOT_A_NUMBER = "A piece of the map is named by whole numbers";
    static final String LOOK_NOT_OFFERED = "That is not a look a station can be given, so nothing was saved";
    static final String REQUEST_INCOMPLETE = "That request arrived without everything it needs, so nothing was done";
    static final String PASSKEY_ENROLMENT_REFUSED = "That passkey could not be set up, so none was saved";
    static final String ENROLMENT_LINK_NOT_GOOD = "That link is no longer good, so no passkey can be set up with it";
    static final String DEVICE_CODE_MISSING = "Type the code the other device is showing";
    static final String DEVICE_CODE_NOT_GOOD = "There is nothing to confirm for that code";
    static final String ACCOUNT_HOLDS_NO_PASSWORD = "This account holds no password";
    static final String PASSKEY_NOT_HERE = "That passkey is not here any more";
    static final String VERIFICATION_CODE_WRONG = "That code was not right, so nothing was confirmed";
    static final String NO_SECOND_FACTOR_SET_UP = "This account has no second factor set up";
    static final String SIGN_IN_NOT_WAITING =
            "That sign-in is no longer waiting for a second factor. Start signing in again";
    static final String SECURITY_KEY_NOT_ACCEPTED = "That security key was not accepted, so nothing was confirmed";
    static final String FACTOR_NOT_HERE = "That second factor is not here any more";
    static final String POLICY_NOT_HERE = "That rule is not here any more";
    static final String SECOND_FACTOR_NOT_RESET = "That second factor could not be reset, so nothing was changed";
    static final String PEER_DID_NOT_ANSWER = "That instance could not be reached";
    static final String FEDERATION_ADDRESS_NOT_PUBLIC = "That address has to be a public HTTPS address";
    static final String PAIR_REQUEST_NOT_HERE = "That pairing request is not here any more";
    static final String FEDERATION_SHARE_NOT_HERE = "That share is not here any more, so nothing was changed";
    static final String BEACON_NOT_RECEIVING = "This instance is not a beacon";
    static final String BEACON_REPORT_NOT_HERE = "That report is not here any more";
    static final String BEACON_ID_NOT_A_NUMBER = "That does not name anything a beacon holds";
    static final String PROBLEM_LOG_NOT_RUNNING = "Nothing is being written down about problems here";
    static final String EQUIPMENT_DATE_MISSING = "Name the day this is about";
    static final String EQUIPMENT_PIECE_MISSING = "Name the piece of gear this is about";
    static final String LOST_ITEM_NOT_CLAIMED = "That find has not been claimed, so nothing was changed";
    static final String INSIGHTS_WINDOW_BACKWARDS = "The span cannot end before it starts";

    static final String ATTENDANCE_SHEET_NOT_HERE = "That attendance sheet is not here any more";
    static final String ATTENDANCE_SHEET_NOT_HERE_ON_WRITE =
            "That attendance sheet is not here any more, so nothing was changed";
    static final String ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE =
            "That entry on the attendance sheet is not here any more, so nothing was changed";
    static final String ATTENDANCE_TEMPLATE_NEEDS_A_NAME = "A template needs a name, so nothing was saved";
    static final String ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE =
            "That template is not here any more, so nothing was changed";
    static final String ATTENDANCE_FIELD_DETAILS_MISSING =
            "A field on the sheet needs a name and a kind, so nothing was saved";
    static final String ATTENDANCE_FIELD_NOT_HERE = "That field on the sheet is not here any more";
    static final String ATTENDANCE_REPORT_PRESET_NOT_HERE = "That saved report is not here any more";
    static final String ABSENCE_SPAN_MISSING = "Give the first and the last day of the absence, so nothing was saved";
    static final String ABSENCE_ENDS_BEFORE_IT_STARTS = "The absence cannot end before it starts, so nothing was saved";
    static final String ABSENCE_NOT_HERE = "That absence is not here any more";
    static final String ABSENCE_NOT_HERE_ON_WRITE = "That absence is not here any more, so nothing was changed";

    static final String FORM_NOT_HERE = "That form is not here any more, so nothing was changed";
    static final String FORM_NOT_ANSWERED_FROM_OUTSIDE = "That form cannot be answered from outside the station";
    static final String FORM_KIND_UNKNOWN = "That is not a kind of form";
    static final String FORM_TAKES_NO_ANSWERS = "This form is not taking answers, so nothing was saved";
    static final String FORM_NOT_YOURS_TO_ANSWER = "This form is not yours to answer";
    static final String FORM_ANSWERS_NOT_SAVED = "The answers could not be saved";
    static final String FORM_ANSWER_NOT_CHANGEABLE = "This form does not let an answer be changed once it is given";

    static final String EVENT_NOT_HERE = "That appointment is not here any more";
    static final String EVENT_REGISTRATION_NOT_HERE = "That registration is not here any more";
    static final String EVENT_CATEGORY_NOT_HERE = "That category is not here any more";
    static final String EVENT_BREAK_NOT_HERE = "That break is not here any more";
    static final String EVENT_TEMPLATE_NOT_HERE = "That template is not here any more";
    static final String NOT_A_STATION_MEMBER = "You are not a member of this station, so nothing was saved";
    static final String MEMBER_NOT_YOURS = "You do not look after this member";
    static final String CANNOT_ANSWER_FOR_MEMBER = "You cannot answer for this member";
    static final String REGISTRATION_CLOSED = "Registration for this appointment has closed. Ask whoever runs it";
    static final String NO_LONGER_TAKEN_BACK = "This can no longer be taken back";
    static final String NO_PLACES_LEFT = "There are no places left";
    static final String DAY_NOT_A_DATE = "The day asked about is not a date";

    static final String COMMENT_NOT_HERE = "That comment is not here any more";
    static final String COMMENT_NEEDS_TEXT = "A comment needs something written in it";

    static final String PAGE_NOT_HERE = "That page is not here any more";

    static final String NEWS_NOT_HERE = "That entry is not here any more";
    static final String NEWS_NOT_HERE_ON_WRITE = "That entry is not here any more, so nothing was changed";
    static final String NEWS_NEEDS_A_TITLE = "An entry needs a title, so nothing was saved";
    static final String NEWS_COMMENT_NOT_YOURS_TO_EDIT = "You can only change a comment you wrote yourself";
    static final String NEWS_COMMENT_NOT_YOURS_TO_DELETE = "You can only delete a comment you wrote yourself";
    static final String PUBLIC_BLOG_NOT_HERE = "This station keeps no public blog";
    static final String ATTACHMENT_NOT_HERE = "That attachment is not here any more";
    static final String UPLOAD_TOO_LARGE = "That file is bigger than this instance takes";

    static final String BOARD_NOT_HERE = "That board is not here any more";
    static final String BOARD_TICKET_NOT_HERE = "That ticket is not here any more";
    static final String BOARD_LABEL_NOT_HERE = "That label is not here any more";
    static final String FEDERATED_BOARD_NOT_HERE = "That shared board is not here any more";
    static final String FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS = "That partner instance is not one this station knows";

    static final String FILE_NOT_HERE = "That file is not here any more";
    static final String FOLDER_NOT_HERE = "That folder is not here any more";
    static final String FILE_TAG_NOT_HERE = "That tag is not here any more";
    static final String PICTURE_NOT_HERE = "That picture is not here";
    static final String UPLOAD_MISSING_FILE = "The upload arrived without a file in it";
    static final String UPLOAD_NOT_SAVED = "The upload could not be saved";
    static final String UPLOAD_NOT_PROCESSED =
            "The upload could not be worked through, so nothing was saved. Trying again may work";

    static final String KB_ARTICLE_NOT_HERE = "That article is not here any more";
    static final String KB_ARTICLE_NEEDS_A_NAME = "An article needs a name, so nothing was saved";
    static final String KB_ARTICLE_HAS_NOTHING_TO_SHOW = "That article has nothing to show";
    static final String KB_ONLY_WRITTEN_AS_PDF = "Only a written article can be handed out as a PDF";
    static final String KB_PDF_NOT_MADE = "That article could not be made into a PDF. Trying again may work";
    static final String KB_ORIGINAL_ONLY_FOR_PRESENTATIONS = "Only a presentation keeps the file it was made from";
    static final String KB_PICTURE_KIND_NOT_TAKEN = "Only PNG, JPEG and WebP pictures are taken here";
    static final String KB_COMMENT_EMPTY = "A comment cannot be empty, so nothing was saved";
    static final String KB_COMMENT_NOT_HERE = "That comment is not here any more";

    static final String PUBLIC_QUIZ_STATION_NOT_REACHED = "No station is reached by this link";
    static final String PUBLIC_QUIZ_CATALOGS_NOT_NAMED = "That link names no catalog to draw a question from";
    static final String QUIZ_QUESTION_NOTE_NOT_HERE = "That note about a question is not here any more";
    static final String QUIZ_QUESTION_NOT_HERE_ON_WRITE = "That question is not here any more, so nothing was changed";
    static final String PROTOCOL_NOT_HERE_BEHIND_RUN = "The protocol this test run follows is not here any more";

    static final String SESSION_HAS_NO_ACCOUNT = "This session does not stand for an account";
    static final String MEMBER_NOT_HERE = "That member is not here any more";
    static final String MEMBER_NOT_YET_FORMER =
            "This member cannot be marked as having left: they may still hold equipment, or a role";
    static final String ACCOUNT_NOT_HERE = "That account is not here any more";
    static final String PROFILE_FIELD_NOT_HERE = "That profile question is not here any more";
    static final String ADDRESS_MISSING = "Give the address to write to";
    static final String ADDRESS_BELONGS_TO_ANOTHER = "That address already belongs to another account";
    static final String LINK_CARRIES_NOTHING = "That link carries nothing to act on";
    static final String PASSWORD_TOO_SHORT = "That password is too short, so nothing was saved";
    static final String EXPIRY_OUT_OF_RANGE =
            "Days before an expiry date cannot be negative and a repeat needs at least one day, so nothing was saved";
    static final String PASSWORD_BREACHED = "That password turns up in known data leaks, so choose another one";
    static final String ACCOUNT_NOT_NAMED = "Name the account this is about";
    static final String PROFILE_FIELD_DETAILS_MISSING = "Give the question a name and a kind";
    static final String PROFILE_FIELD_AUDIENCE_AMBIGUOUS =
            "Name either a kind of member or a group, and only one of the two";
    static final String GROUP_NAME_MISSING = "Give the group a name";
    static final String GROUP_NOT_HERE = "That group is not here any more";
    static final String GROUP_NOT_HERE_NOTHING_SAVED = "One of those groups is not here, so nothing was saved";
    static final String GROUP_WRONG_USER_TYPE = "That group takes only members of certain types, so nothing was saved";
    static final String GROUP_GRANTS_MORE_THAN_YOURS =
            "That group grants permissions you do not hold yourself, so nothing was saved";
    static final String GROUP_SET_NOT_HERE = "That set of groups is not here any more";
    static final String GROUP_SET_NAME_MISSING = "Give the set of groups a name";
    static final String GROUP_SET_NAME_TAKEN = "Another set of groups already has that name";
    static final String TAG_NAME_MISSING = "Give the tag a name";
    static final String MEMBER_TAG_NOT_HERE = "That tag is not here any more";
    static final String TRANSFER_TOKEN_NOT_GOOD = "That transfer is not one this instance is carrying out any more";
    static final String REGISTRATION_CODE_NOT_HERE = "That registration code is not here any more";

    static final String DOCUMENT_NOT_HERE = "That document is not here any more";
    static final String DOCUMENT_NOT_YOURS = "That document belongs to somebody you do not answer for";
    static final String PAGE_NEEDS_A_TITLE = "A page needs a title, so nothing was saved";
    static final String PROCEDURE_TEMPLATE_NEEDS_A_NAME = "A procedure template needs a name, so nothing was saved";
    static final String PROCEDURE_STEP_NEEDS_A_TITLE = "A step needs a title, so nothing was saved";
    static final String PROCEDURE_STEP_NOT_HERE_ON_WRITE =
            "That step of the procedure is not here any more, so nothing was changed";
    static final String CHECKLIST_NEEDS_A_NAME = "A checklist needs a name, so nothing was saved";
    static final String CHECKLIST_PDF_NOT_MADE = "That checklist could not be made into a PDF. Trying again may work";
    static final String DOCUMENT_NOT_YOURS_TO_ADD = "You may not add a document for this member";

    static final String ITEM_NOT_HERE = "That piece of gear is not here any more";
    static final String ITEM_NEEDS_A_NAME = "A piece of gear needs a name, so nothing was saved";
    static final String ITEM_KIND_NOT_HERE = "That kind of gear is not here any more";
    static final String INVENTORY_NOT_HERE = "That inventory is not here any more";
    static final String INVENTORY_NOT_HERE_BEHIND_ITEM =
            "The inventory this piece of gear belongs to is not here any more";
    static final String INVENTORY_NEEDS_A_NAME = "An inventory needs a name, so nothing was saved";
    static final String INVENTORY_NEEDS_A_KIND = "An inventory needs a kind, so nothing was saved";
    static final String SIZE_NOT_HERE = "That size is not here any more";
    static final String SIZE_NEEDS_A_NAME = "A size needs a name, so nothing was saved";
    static final String REQUIREMENT_NOT_HERE = "That requirement is not here any more";
    static final String NO_CODE_GIVEN = "Name the code to look for";
    static final String LOSS_NOT_YOURS_TO_REPORT = "Only somebody holding this gear can report it missing";
    static final String NOTHING_TO_EXPORT = "There was nothing to put in that list, so no file was made";
    static final String NO_FLOW_FOR_THIS_MOVEMENT = "No chain of steps is set up for a movement like this one";
    static final String MOVEMENT_PURPOSE_MISSING = "Say what the movement is for";
    static final String MOVEMENT_DOCUMENT_NOT_HERE = "The file attached to this movement is not here any more";
    static final String FLOW_NOT_HERE = "That chain of steps is not here any more";
    static final String FLOW_STEP_NOT_HERE = "That step is not here any more";
    static final String CONTAINER_NOT_HERE = "That container is not here any more";
    static final String CONTAINER_NOT_SAVED = "The container could not be saved, so nothing was changed. "
            + "It needs a name without a slash, a place to sit that is here and not inside itself, "
            + "and a code nothing else here already uses";
    static final String CONTAINER_KIND_NOT_HERE = "That kind of container is not here any more";
    static final String CONTAINER_KIND_NOT_CREATED =
            "A kind of container needs a short key of its own and a name, so nothing was saved";
    static final String FIELD_NOT_HERE = "That field is not here any more";
    static final String CHECK_WITHOUT_ITEMS = "The check arrived with no gear in it, so nothing was saved";
    static final String PROCUREMENT_NOT_HERE = "That procurement is not here any more";

    static final String QUIZ_CATALOG_NOT_HERE = "That catalog is not here any more";
    static final String QUIZ_CATEGORY_NOT_HERE = "That category is not here any more";
    static final String QUIZ_TEST_NOT_HERE = "That test is not here any more";
    static final String QUIZ_ATTEMPT_NOT_HERE = "That attempt is not here any more";

    static final String PROCEDURE_NOT_YOURS = "That procedure was not handed to you";

    static final String STATION_APPLICATION_NOT_HERE = "That application is not here any more";

    static final String WAITING_LIST_NOT_HERE = "That waiting list is not here any more";
    static final String WAITING_LIST_ENTRY_NOT_HERE = "That entry is not here any more";
    static final String WAITING_LIST_FIELD_NOT_HERE = "That question is not on this waiting list any more";
    static final String WAITING_LIST_INVITE_UNKNOWN = "No waiting list is reached by this invite";
    static final String SIGN_IN_FIRST = "Sign in before doing this";
    static final String DEFAULT_NOT_SUITING = "That default does not suit this question, so nothing was saved";
    static final String BORROWED_GEAR_LOST_AT_PARTNER =
            "This gear belongs to a partner station. Tell them on the lending request it came in on";
    static final String BEACON_PROTOCOL_TOO_NEW = "This beacon does not speak that version of the protocol yet";
    static final String DEMO_UPLOADS_OFF = "Uploading files is switched off in the demo, so nothing was saved";
    static final String STATION_BEING_TRANSFERRED =
            "This station is moving to another instance, so nothing can be changed until the move is done";
    static final String CLUSTER_TAG_TAKEN =
            "The cluster already recommends that word to these stations, so nothing was saved";
    static final String CLUSTER_TAG_NOT_HERE = "That recommended word is not here any more";
    static final String CLUSTER_NEEDS_A_NAME = "A cluster needs a name, so nothing was saved";
    static final String STATION_NOT_IN_CLUSTER = "That station does not belong to this cluster, so nothing was changed";
    static final String CLUSTER_POOL_TOO_SMALL =
            "That is more room than the cluster has left to hand out, so nothing was changed";
    static final String MOVEMENT_NOT_HERE = "That movement is not here any more";
    static final String MOVEMENT_FLOW_GONE =
            "The chain of steps this movement walked is not here any more, so nothing was changed";
    static final String ART_NAME_TAKEN = "This inventory already has a kind by that name, so nothing was saved";
    static final String INVENTORY_TAG_NOT_HERE = "That tag is not here any more";
    static final String SELF_CHECK_NOT_HERE = "That self-check is not here any more";
    static final String SELF_CHECK_ANSWER_ALREADY_SETTLED = "Somebody has already settled this answer";
    static final String ITEM_NOT_ON_MEMBERS_RECORD = "That piece of gear is not on this member's record";
    static final String INVENTORY_CHECK_MEMBER_TAKEN = "Somebody else is already checking this member";
    static final String SELF_CHECK_SIZE_NOT_OFFERED = "That size is not one this kind of gear comes in";
    static final String BORROWED_SHELF_ONLY_BORROWED =
            "This shelf only holds gear borrowed from a partner station, so nothing was saved";
    static final String INVENTORY_KIND_ELSEWHERE =
            "That kind of gear belongs to another inventory, so nothing was saved";
    static final String QUESTION_DEFAULT_NOT_ACCEPTED =
            "A question cannot start from a value it would not take as an answer, so nothing was saved";
    static final String EVENT_ENDS_BEFORE_IT_STARTS =
            "An appointment cannot end before it starts, so nothing was saved";
    static final String INTERNAL_FORM_HAS_NO_LINK = "A form for the station's own members is not sent by link";
    static final String QUIZ_CATALOG_EXAMPLE_NOT_READ =
            "The example sheet could not be read. This is a fault in Ember; please report it";
    static final String QUIZ_CATALOG_FILE_NOT_ONE = "That file is not a catalog export, so nothing was imported";
    static final String WAITING_LIST_HAS_A_BIRTH_DATE =
            "This list already asks for a date of birth, so nothing was saved";
    static final String WAITING_LIST_ANSWER_NOT_ACCEPTED =
            "An answer does not suit its question on this list, so nothing was saved";
    static final String REGISTRATION_HELD_BY_A_FIELD =
            "This place comes from a question of the appointment and is taken back there, so nothing was changed";
    static final String PARTNER_STATION_NOT_HERE = "That partner station is not here any more";
    static final String MAILBOX_NOT_HERE = "That mailbox is not here any more";
    static final String MAILBOX_RULE_NOT_HERE = "That mailbox rule is not here any more";

    private Sentences() {}
}
