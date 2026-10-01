/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#INVENTORY}: inventory.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum InventoryRefusal implements Refusal {
    /** A piece of gear asked for by a route that then checks which station it is at. */
    ITEM_NOT_HERE(1, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** The inventory a piece of gear was written down in, gone while the piece is still here. */
    INVENTORY_NOT_HERE_BEHIND_ITEM(2, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** Gear belonging to an owner the reader may write nothing down for. */
    GEAR_OWNER_NOT_YOURS_TO_CREATE(3, HttpStatus.FORBIDDEN, "You may not write down gear that belongs to that owner"),

    /** An inventory asked for by a route that then checks which station it is at. */
    INVENTORY_NOT_HERE(4, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /**
     * A requirement that is gone, or belongs to another station. One code deliberately: the one
     * lookup asks only about this station's own, and telling the two apart would say that the
     * requirement exists at a station the reader may not see.
     */
    REQUIREMENT_NOT_HERE(5, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** The inventory a piece was to be taken into and handed over from, which is gone. */
    INVENTORY_NOT_HERE_ON_HAND_OUT(6, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A member whose gear was asked about and who is not at this station. */
    MEMBER_NOT_HERE_FOR_GEAR(7, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An inventory being written down without a name. */
    INVENTORY_NEEDS_A_NAME(8, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_NAME),

    /** An inventory being written down without saying what kind of thing it holds. */
    INVENTORY_NEEDS_A_KIND(9, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_KIND),

    /** An inventory that went between the list being drawn and it being opened. */
    INVENTORY_NOT_HERE_ON_READ(10, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A change to an inventory that would leave it without a name. */
    INVENTORY_NEEDS_A_NAME_ON_CHANGE(11, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_NAME),

    /** A change to an inventory that would leave it without a kind. */
    INVENTORY_NEEDS_A_KIND_ON_CHANGE(12, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_KIND),

    /** An inventory that went before the change to it could be written. */
    INVENTORY_NOT_CHANGED(13, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** An inventory that was already gone when its deletion was asked for. */
    INVENTORY_NOT_DELETED(14, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A size being written down without a name. */
    SIZE_NEEDS_A_NAME(15, HttpStatus.BAD_REQUEST, Sentences.SIZE_NEEDS_A_NAME),

    /** A change to a size that would leave it without a name. */
    SIZE_NEEDS_A_NAME_ON_CHANGE(16, HttpStatus.BAD_REQUEST, Sentences.SIZE_NEEDS_A_NAME),

    /** A size that went before the change to it could be written. */
    SIZE_NOT_CHANGED(17, HttpStatus.NOT_FOUND, Sentences.SIZE_NOT_HERE),

    /** A size that was already gone when its deletion was asked for. */
    SIZE_NOT_DELETED(18, HttpStatus.NOT_FOUND, Sentences.SIZE_NOT_HERE),

    /** A piece of gear being written down without a name. */
    ITEM_NEEDS_A_NAME(19, HttpStatus.BAD_REQUEST, Sentences.ITEM_NEEDS_A_NAME),

    /** A stock-taking that hands pieces to members, sent by somebody who may hand none out. */
    HANDING_OUT_NOT_ALLOWED(20, HttpStatus.FORBIDDEN, "You may not hand a piece of gear to a member"),

    /** A search for a piece of gear that names no code to search by. */
    NO_CODE_GIVEN_FOR_ITEM(21, HttpStatus.BAD_REQUEST, Sentences.NO_CODE_GIVEN),

    /** A code no piece of gear at this station carries. */
    ITEM_NOT_HERE_BY_CODE(22, HttpStatus.NOT_FOUND, "No piece of gear here carries that code"),

    /** A piece of gear that went between the list being drawn and it being opened. */
    ITEM_NOT_HERE_ON_READ(23, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A change to a piece of gear that would leave it without a name. */
    ITEM_NEEDS_A_NAME_ON_CHANGE(24, HttpStatus.BAD_REQUEST, Sentences.ITEM_NEEDS_A_NAME),

    /** A piece of gear that went before the change to it could be written. */
    ITEM_NOT_CHANGED(25, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that went before it could be moved into another inventory. */
    ITEM_NOT_MOVED(26, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that went before it could be handed over or taken back. */
    ITEM_NOT_ASSIGNED(27, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear whose whereabouts were asked for and which is gone. */
    ITEM_NOT_HERE_ON_LOCATION(28, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that went before it could be put into a container. */
    ITEM_NOT_PUT_IN_CONTAINER(29, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear and a container that do not go together. */
    ITEM_NOT_FOR_THIS_CONTAINER(
            30, HttpStatus.BAD_REQUEST, "That piece of gear cannot be put in that container, so nothing was saved"),

    /** A piece of gear being reported missing that is gone. */
    ITEM_NOT_HERE_ON_LOSS(31, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A loss reported without a note at a station that asks for one. */
    LOSS_NEEDS_A_NOTE(
            32, HttpStatus.BAD_REQUEST, "This station asks for a note when gear goes missing, so nothing was saved"),

    /** A piece of gear that went between being read and being marked missing. */
    ITEM_NOT_MARKED_LOST(33, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A loss reported for gear in nobody's hands, or by a session that stands for no member. */
    LOSS_NOT_YOURS_TO_REPORT(34, HttpStatus.FORBIDDEN, Sentences.LOSS_NOT_YOURS_TO_REPORT),

    /** A loss reported for gear held by somebody the reader does not answer for. */
    LOSS_NOT_YOURS_TO_REPORT_FOR_THEM(35, HttpStatus.FORBIDDEN, Sentences.LOSS_NOT_YOURS_TO_REPORT),

    /** A loss report whose attached file could not be read. */
    LOSS_REPORT_FILE_UNREADABLE(36, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was saved"),

    /** A piece of gear that went before it could be marked as found again. */
    ITEM_NOT_MARKED_FOUND(37, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that was already gone when its deletion was asked for. */
    ITEM_NOT_DELETED(38, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A requirement written down without saying which inventory it is about. */
    REQUIREMENT_NEEDS_AN_INVENTORY(
            39, HttpStatus.BAD_REQUEST, "A requirement has to say which inventory it is about, so nothing was saved"),

    /** A requirement written down without saying who it applies to. */
    REQUIREMENT_NEEDS_SOMEBODY_TO_APPLY_TO(
            40,
            HttpStatus.BAD_REQUEST,
            "A requirement has to say which kind of member or which group it applies to, so nothing was saved"),

    /** A requirement that went before the change to it could be written. */
    REQUIREMENT_NOT_CHANGED(41, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** A requirement that went before it could be moved up or down the list. */
    REQUIREMENT_NOT_MOVED(42, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** A requirement that was already gone when its deletion was asked for. */
    REQUIREMENT_NOT_DELETED(43, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** A list of members and their gear that came out with nothing in it. */
    MEMBER_GEAR_LIST_EMPTY(44, HttpStatus.BAD_REQUEST, Sentences.NOTHING_TO_EXPORT),

    /** A movement whose ends name no chain of steps to walk. */
    NO_FLOW_FOR_THIS_MOVEMENT(45, HttpStatus.NOT_FOUND, Sentences.NO_FLOW_FOR_THIS_MOVEMENT),

    /** The chain of steps a movement is bound to, which is gone. */
    FLOW_NOT_HERE_BEHIND_BINDING(46, HttpStatus.NOT_FOUND, Sentences.NO_FLOW_FOR_THIS_MOVEMENT),

    /** A question about which chain would be walked that does not say what the movement is for. */
    MOVEMENT_PURPOSE_MISSING(47, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_PURPOSE_MISSING),

    /** A purpose spelled in a way no movement can have. */
    MOVEMENT_PURPOSE_NOT_KNOWN(48, HttpStatus.BAD_REQUEST, "That is not something a movement can be for"),

    /** Something in the address that has to be a number and is not one. */
    NUMBER_EXPECTED_IN_ADDRESS(49, HttpStatus.BAD_REQUEST, "A number was expected here and this is not one"),

    /** A chain of steps being written down without saying what it is for. */
    FLOW_NEEDS_A_PURPOSE(
            50, HttpStatus.BAD_REQUEST, "A chain of steps has to say what it is for, so nothing was saved"),

    /** A chain of steps that went before its new name could be written. */
    FLOW_NOT_RENAMED(51, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain of steps that went between being renamed and being read back. */
    FLOW_NOT_HERE_AFTER_RENAME(52, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain of steps that was already gone when its retirement was asked for. */
    FLOW_NOT_ARCHIVED(53, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A step that went before the change to it could be written. */
    FLOW_STEP_NOT_CHANGED(54, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A step that was already gone when its retirement was asked for. */
    FLOW_STEP_NOT_ARCHIVED(55, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A chain pointed at a combination that names neither an owner nor a purpose. */
    BINDING_NEEDS_AN_OWNER_AND_A_PURPOSE(
            56,
            HttpStatus.BAD_REQUEST,
            "Say whose gear this is about and what the movement is for, so nothing was saved"),

    /** An order for the steps of a chain that names no step at all. */
    STEP_ORDER_NAMES_NO_STEPS(
            57, HttpStatus.BAD_REQUEST, "Name the steps in the order they are to be walked, so nothing was saved"),

    /** A step written down without saying who confirms it, what it is about, or where it leaves the gear. */
    STEP_NEEDS_ITS_PARTS(
            58,
            HttpStatus.BAD_REQUEST,
            "A step has to say who confirms it, what it is about and where the gear is left, so nothing was saved"),

    /**
     * A chain of steps that is gone, or belongs to another station. The two are one code
     * deliberately: telling them apart would say that the chain exists at a station the reader may
     * not see.
     */
    FLOW_NOT_HERE(59, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A step asked for by a route that then checks whose chain it belongs to. */
    FLOW_STEP_NOT_HERE(60, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A chain of steps that went between being changed and being read back. */
    FLOW_NOT_HERE_AFTER_CHANGE(61, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A container asked for by a route that then checks which station it is at. */
    CONTAINER_NOT_HERE(62, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A kind of container that was read and then refused for what it said. */
    CONTAINER_KIND_NOT_CREATED(63, HttpStatus.BAD_REQUEST, Sentences.CONTAINER_KIND_NOT_CREATED),

    /** A kind of container that is gone, or belongs to another station. */
    CONTAINER_KIND_NOT_HERE(64, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A kind of container that went before the change to it could be written. */
    CONTAINER_KIND_NOT_CHANGED(65, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A change to a kind of container that would leave it without a name. */
    CONTAINER_KIND_NEEDS_A_NAME(66, HttpStatus.BAD_REQUEST, "A kind of container needs a name, so nothing was saved"),

    /** A kind of container that was already gone when its deletion was asked for. */
    CONTAINER_KIND_NOT_HERE_ON_DELETE(67, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A kind of container that went between being read and being deleted. */
    CONTAINER_KIND_NOT_DELETED(68, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A container that was read and then refused for what it said. */
    CONTAINER_NOT_CREATED(69, HttpStatus.BAD_REQUEST, Sentences.CONTAINER_NOT_SAVED),

    /** A container that went before the change to it could be written. */
    CONTAINER_NOT_HERE_ON_CHANGE(70, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A change to a container that was read and then refused for what it said. */
    CONTAINER_NOT_CHANGED(71, HttpStatus.BAD_REQUEST, Sentences.CONTAINER_NOT_SAVED),

    /** A container that was already gone when its deletion was asked for. */
    CONTAINER_NOT_DELETED(72, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A search for a container that names no code to search by. */
    NO_CODE_GIVEN_FOR_CONTAINER(73, HttpStatus.BAD_REQUEST, Sentences.NO_CODE_GIVEN),

    /** A code no container at this station carries. */
    CONTAINER_NOT_HERE_BY_CODE(74, HttpStatus.NOT_FOUND, "No container here carries that code"),

    /** A movement started without saying what it is for. */
    MOVEMENT_NEEDS_A_PURPOSE(75, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_PURPOSE_MISSING),

    /** A movement raised for a member the reader neither is nor answers for. */
    MEMBER_NOT_YOURS_TO_ACT_FOR(76, HttpStatus.FORBIDDEN, "You do not answer for this member"),

    /** A piece written down as newly arrived on a movement that is about no inventory. */
    ARRIVAL_HAS_NOWHERE_TO_GO(
            77,
            HttpStatus.BAD_REQUEST,
            "This movement is about no inventory, so a new piece of gear has nowhere to go and nothing was saved"),

    /** A piece written down as newly arrived where the owner already says what it sent. */
    ARRIVAL_NAMED_BY_THE_OWNER(
            78,
            HttpStatus.BAD_REQUEST,
            "The owner says what it sends, so pick the piece that arrived rather than writing down a new one"),

    /** A piece written down as newly arrived without a name. */
    ARRIVAL_NEEDS_A_NAME(79, HttpStatus.BAD_REQUEST, Sentences.ITEM_NEEDS_A_NAME),

    /** Everything a member holds asked back without saying which member. */
    RETURN_OF_EVERYTHING_NEEDS_A_MEMBER(80, HttpStatus.BAD_REQUEST, "Say which member is to hand everything back"),

    /** A member named for a return who is not at this station. */
    MEMBER_NOT_AT_THIS_STATION(81, HttpStatus.BAD_REQUEST, "That member is not at this station"),

    /** A movement whose attached file was never written down. */
    MOVEMENT_DOCUMENT_NOT_HERE(82, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_DOCUMENT_NOT_HERE),

    /** A movement whose attached file is written down but whose stored copy is gone. */
    MOVEMENT_DOCUMENT_NOT_READ(83, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_DOCUMENT_NOT_HERE),

    /** A list of movements that came out with nothing in it. */
    MOVEMENT_LIST_EMPTY(84, HttpStatus.NOT_FOUND, Sentences.NOTHING_TO_EXPORT),

    /** A correction that would leave a piece of gear somewhere a movement cannot put it. */
    CUSTODY_NOT_ONE_OF_THESE(
            85,
            HttpStatus.BAD_REQUEST,
            "A movement can put a piece of gear with its owner, at a station, with a member or in the post"),

    /** A movement that went between being read and being deleted. */
    MOVEMENT_NOT_DELETED(86, HttpStatus.NOT_FOUND, "That movement is not here any more"),

    /**
     * A movement that is gone, or one this reader may not see. The two are one code deliberately:
     * telling them apart would say that the movement exists somewhere they are not shown.
     */
    MOVEMENT_NOT_HERE_OR_NOT_YOURS(87, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** The inventory a field is written on, asked for before the field itself. */
    INVENTORY_NOT_HERE_BEHIND_FIELD(88, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A field that is gone, or belongs to another inventory. */
    FIELD_NOT_IN_THIS_INVENTORY(89, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A piece of gear whose fields were asked for and which is gone. */
    ITEM_NOT_HERE_ON_FIELDS(90, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A field that was read and then refused for what it said. */
    FIELD_NOT_CREATED(
            91,
            HttpStatus.BAD_REQUEST,
            "That field could not be saved: it needs a short key of its own, a name and a kind of answer, "
                    + "and it belongs either to a kind of gear or to one piece, never to both"),

    /** A field that went before the change to it could be written. */
    FIELD_NOT_HERE_ON_CHANGE(92, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A change to a field that was read and then refused for what it said. */
    FIELD_NOT_CHANGED(
            93,
            HttpStatus.BAD_REQUEST,
            "A field needs a name, and its settings have to match the kind of answer it takes, so nothing was saved"),

    /** A field that was already gone when its deletion was asked for. */
    FIELD_NOT_DELETED(94, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A self-check saved with no answers in it at all. */
    SELF_CHECK_WITHOUT_ANSWERS(
            95, HttpStatus.BAD_REQUEST, "The check arrived without any answers in it, so nothing was saved"),

    /** A loss or a swap held back for review that says neither which it is nor about what. */
    HELD_REPORT_INCOMPLETE(
            96,
            HttpStatus.BAD_REQUEST,
            "Say what is being reported and about which piece of gear, so nothing was saved"),

    /** A day something is due that cannot be read as a day. */
    DUE_DAY_NOT_A_DATE(97, HttpStatus.BAD_REQUEST, "The day this is due is not a date, so nothing was saved"),

    /** A container whose contents were to be checked and which is gone. */
    CONTAINER_NOT_HERE_ON_CHECK(98, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A piece of gear named in a check that is gone. */
    ITEM_NOT_HERE_ON_CHECK(99, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** The inventory a checked piece of gear belongs to, gone while the piece is still here. */
    INVENTORY_NOT_HERE_BEHIND_CHECKED_ITEM(100, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** An inventory whose stock was to be checked and which is gone. */
    INVENTORY_NOT_HERE_ON_CHECK(101, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A member whose gear was to be checked and who is not at this station. */
    MEMBER_NOT_HERE_ON_CHECK(102, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A check of a container saved with no gear in it at all. */
    CONTAINER_CHECK_WITHOUT_ITEMS(103, HttpStatus.BAD_REQUEST, Sentences.CHECK_WITHOUT_ITEMS),

    /** A check of a member saved with no gear in it at all. */
    MEMBER_CHECK_WITHOUT_ITEMS(104, HttpStatus.BAD_REQUEST, Sentences.CHECK_WITHOUT_ITEMS),

    /** The last check of a member who has never been checked. */
    NO_CHECK_YET_FOR_MEMBER(105, HttpStatus.NOT_FOUND, "This member has not been checked yet"),

    /** The inventory a kind of gear belongs to, asked for before the kind itself. */
    INVENTORY_NOT_HERE_BEHIND_KIND(106, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A kind of gear that is gone, or belongs to another inventory. */
    ITEM_KIND_NOT_IN_INVENTORY(107, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind of gear that went before the change to it could be written. */
    ITEM_KIND_NOT_CHANGED(108, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind of gear that was already gone when its deletion was asked for. */
    ITEM_KIND_NOT_DELETED(109, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** The inventory something was to be ordered for, which is gone or belongs to another station. */
    INVENTORY_NOT_HERE_ON_PROCUREMENT(110, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** An order that went before it could be marked as delivered. */
    PROCUREMENT_NOT_FULFILLED(111, HttpStatus.NOT_FOUND, Sentences.PROCUREMENT_NOT_HERE),

    /** An order that was already gone when its deletion was asked for. */
    PROCUREMENT_NOT_DELETED(112, HttpStatus.NOT_FOUND, Sentences.PROCUREMENT_NOT_HERE),

    /** A movement that could not be read back after being moved onto another chain of steps. */
    MOVEMENT_NOT_HERE_AFTER_RECHAIN(113, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A field of an inventory given a type an inventory does not offer. */
    INVENTORY_FIELD_TYPE_NOT_OFFERED(
            114, HttpStatus.BAD_REQUEST, "Inventories do not offer that type of field, so nothing was saved"),

    /** A chain of steps that went before whether it waits for the member's receipt could be written. */
    FLOW_RECEIPT_NOT_CHANGED(275, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A movement started on a chain whose steps, once those a loss report skips are left out, are none. */
    MOVEMENT_FLOW_HAS_NO_STEPS(
            115, HttpStatus.BAD_REQUEST, "That chain of steps has no steps to walk, so the movement was not started"),

    /** A correction to a movement sent without the reason it has to carry. */
    MOVEMENT_CORRECTION_NEEDS_A_REASON(
            116, HttpStatus.BAD_REQUEST, "Correcting a movement needs a reason saying why, so nothing was changed"),

    /** A correction to a movement that is not here. */
    MOVEMENT_NOT_HERE_TO_CORRECT(117, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_NOT_HERE),

    /** A correction to a movement whose chain of steps has since been removed. */
    MOVEMENT_FLOW_GONE_BEFORE_CORRECTION(118, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_FLOW_GONE),

    /** A movement started on a piece of gear that has since been removed. */
    MOVEMENT_PIECE_NOT_HERE(
            119, HttpStatus.BAD_REQUEST, "That piece of gear is no longer recorded, so no movement was started"),

    /** A movement started on a piece that is already leaving on another open movement. */
    MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT(
            120,
            HttpStatus.BAD_REQUEST,
            "This piece of gear is already on another movement, so finish or call that one off first"),

    /** A movement started on a piece another open movement has already promised to somebody. */
    MOVEMENT_PIECE_ALREADY_PROMISED(
            121,
            HttpStatus.BAD_REQUEST,
            "This piece of gear is promised to another movement, so finish or call that one off first"),

    /** An exchange started in an inventory of different things, which has nothing to swap a piece for. */
    MOVEMENT_NOTHING_TO_SWAP_FOR(
            122,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so there is nothing to swap a piece for"),

    /** A step forced without the note saying why. */
    MOVEMENT_FORCE_NEEDS_A_NOTE(
            123, HttpStatus.BAD_REQUEST, "Forcing a step needs a note saying why, so nothing was changed"),

    /** A step acknowledged or forced that is not the one the movement stands on. */
    MOVEMENT_NOT_ON_THAT_STEP(
            124, HttpStatus.BAD_REQUEST, "The movement is not standing on that step any more, so nothing was changed"),

    /** The step a movement stands on, gone from its chain before it could be walked. */
    MOVEMENT_STEP_GONE(125, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A step forced that belongs to the station itself, which can simply acknowledge it. */
    MOVEMENT_STATION_STEP_NOT_FORCED(
            126, HttpStatus.BAD_REQUEST, "This step is the station's own, so acknowledge it rather than forcing it"),

    /** A step that names the arriving piece, walked without naming one. */
    MOVEMENT_STEP_NEEDS_THE_ARRIVING_PIECE(
            127, HttpStatus.BAD_REQUEST, "This step names the piece of gear that arrives, so choose it first"),

    /** A movement called off by somebody whose turn it no longer is and who no longer holds the piece. */
    MOVEMENT_NOT_YOURS_TO_CANCEL(
            128, HttpStatus.FORBIDDEN, "This movement is not on your side any more, so it was not called off"),

    /** A step walked on a movement that is not here. */
    MOVEMENT_NOT_HERE_TO_WALK(129, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_NOT_HERE),

    /** A step walked on a movement that is already finished or called off. */
    MOVEMENT_ALREADY_CLOSED(
            130, HttpStatus.BAD_REQUEST, "This movement is already finished or called off, so nothing was changed"),

    /** A step walked on a movement whose chain of steps has since been removed. */
    MOVEMENT_FLOW_GONE(131, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_FLOW_GONE),

    /** A step acknowledged by somebody it does not belong to. */
    MOVEMENT_STEP_NOT_YOUR_TURN(
            132, HttpStatus.FORBIDDEN, "This step is for somebody else to take, so nothing was changed"),

    /** A movement moved onto the chain it belongs on, which has no steps to stand on. */
    MOVEMENT_RECHAIN_FLOW_HAS_NO_STEPS(
            133,
            HttpStatus.BAD_REQUEST,
            "The chain this movement belongs on has no steps to stand on, so nothing was changed"),

    /** A movement moved onto its chain without a step, where the chain does not say on its own where it stands. */
    MOVEMENT_RECHAIN_LANDING_NOT_CLEAR(
            134,
            HttpStatus.BAD_REQUEST,
            "The chain this movement belongs on does not say where it would stand, so choose the step"),

    /** A movement moved onto its chain at a step the chain does not have. */
    MOVEMENT_RECHAIN_LANDING_OUT_OF_RANGE(
            135,
            HttpStatus.BAD_REQUEST,
            "The chain this movement belongs on has no step at that place, so nothing was changed"),

    /** A movement moved onto another chain that is not here. */
    MOVEMENT_NOT_HERE_TO_RECHAIN(136, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_NOT_HERE),

    /** A movement moved onto another chain after it has finished. */
    MOVEMENT_FINISHED_BEFORE_RECHAIN(
            137,
            HttpStatus.BAD_REQUEST,
            "That movement has finished, so the chain under it no longer decides anything"),

    /** A chain written again from its preset that is not here. */
    MOVEMENT_FLOW_NOT_HERE_TO_RESTORE(138, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain written again from its preset that belongs to the association above the station. */
    MOVEMENT_FLOW_RESTORE_BELONGS_TO_ASSOCIATION(
            139,
            HttpStatus.BAD_REQUEST,
            "That chain belongs to the association above the station, so the station cannot write it again"),

    /** A chain written again from its preset while nothing is bound to it. */
    MOVEMENT_FLOW_RESTORE_NOT_BOUND(
            140, HttpStatus.BAD_REQUEST, "Nothing is bound to that chain, so there is no preset to write it from"),

    /** A chain written again from its preset where no preset covers what it is bound to. */
    MOVEMENT_FLOW_RESTORE_NO_PRESET(
            141,
            HttpStatus.BAD_REQUEST,
            "No preset covers what that chain is bound to, so there is none to write it from"),

    /** A chain written again while movements on one of its steps have nowhere to land in the new one. */
    MOVEMENT_FLOW_RESTORE_LANDING_MISSING(
            142,
            HttpStatus.BAD_REQUEST,
            "Some movements have nowhere to stand in the new chain, so say where they land"),

    /** A landing for a chain written again that names no step to land on. */
    MOVEMENT_FLOW_RESTORE_LANDING_NAMES_NO_STEP(
            143, HttpStatus.BAD_REQUEST, "Every landing has to name the step it lands on, so nothing was changed"),

    /** A landing given for a step of the chain no movement stands on. */
    MOVEMENT_FLOW_RESTORE_LANDING_FOR_EMPTY_STEP(
            144,
            HttpStatus.BAD_REQUEST,
            "No movement stands on that step, so it needs no landing and nothing was changed"),

    /** A landing on a step the preset does not write. */
    MOVEMENT_FLOW_RESTORE_LANDING_OUT_OF_RANGE(
            145, HttpStatus.BAD_REQUEST, "The preset has no step at that place to land on, so nothing was changed"),

    /** Two landings given for the movements on the same step. */
    MOVEMENT_FLOW_RESTORE_LANDING_TWICE(
            146,
            HttpStatus.BAD_REQUEST,
            "A step was given two landings, and its movements can only stand on one, so nothing was changed"),

    /** A movement asked for a combination of owner, purpose and party no chain is bound to at the station. */
    MOVEMENT_FLOW_NOT_BOUND(147, HttpStatus.BAD_REQUEST, Sentences.NO_FLOW_FOR_THIS_MOVEMENT),

    /** A step of a chain changed that is not here. */
    MOVEMENT_FLOW_STEP_NOT_HERE_TO_CHANGE(148, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A step of a chain archived that is not here. */
    MOVEMENT_FLOW_STEP_NOT_HERE_TO_ARCHIVE(149, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A chain whose purpose was asked for, gone between being named and being read. */
    MOVEMENT_FLOW_NOT_HERE_FOR_PURPOSE(150, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain bound to a combination that is not here. */
    MOVEMENT_FLOW_NOT_HERE_TO_BIND(151, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain bound at a station it does not belong to. */
    MOVEMENT_FLOW_NOT_YOURS_TO_BIND(
            152, HttpStatus.BAD_REQUEST, "That chain belongs to somebody else, so it was not bound"),

    /** A chain bound to a combination whose purpose is not the chain's own. */
    MOVEMENT_FLOW_BOUND_TO_OTHER_PURPOSE(
            153, HttpStatus.BAD_REQUEST, "That chain is for a different kind of movement, so it was not bound"),

    /** A piece handed out at the counter that an open movement has promised to somebody. */
    CUSTODY_PIECE_PROMISED(
            154,
            HttpStatus.BAD_REQUEST,
            "This piece of gear is promised to a movement, so hand it over there or call that one off"),

    /** A piece handed out that is missing, with a partner, or otherwise somewhere it cannot be handed out of. */
    CUSTODY_NOT_HANDED_OUT_FROM_HERE(
            155,
            HttpStatus.BAD_REQUEST,
            "This piece of gear cannot be handed out from where it is now, so nothing was changed"),

    /** Gear borrowed from a partner station marked missing here rather than on the lending request. */
    CUSTODY_BORROWED_NOT_MARKED_LOST(156, HttpStatus.BAD_REQUEST, Sentences.BORROWED_GEAR_LOST_AT_PARTNER),

    /** A step that hands gear to a member, walked on a movement that names no member. */
    CUSTODY_STEP_NAMES_NO_MEMBER(
            157,
            HttpStatus.BAD_REQUEST,
            "This step hands the gear to a member but the movement names none, so nothing was changed"),

    /** A loss reported for a piece of gear that is not here. */
    LOSS_REPORT_PIECE_NOT_HERE(158, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A loss reported for gear that is not recorded as missing. */
    LOSS_REPORT_PIECE_NOT_MISSING(
            159, HttpStatus.BAD_REQUEST, "This gear is not recorded as missing, so there is nothing to report"),

    /** A loss reported for gear the station owns itself, which has nobody above it to report to. */
    LOSS_REPORT_STATION_OWNS_IT(
            160, HttpStatus.BAD_REQUEST, "The station owns this gear itself, so there is nobody to report it to"),

    /** A loss reported for gear whose owning association is not here. */
    LOSS_REPORT_OWNER_NOT_HERE(
            161,
            HttpStatus.NOT_FOUND,
            "The association that owns this gear is not here to answer, so nothing was reported"),

    /** A loss reported without the note the owning association asks for. */
    LOSS_REPORT_NEEDS_A_NOTE(
            162,
            HttpStatus.BAD_REQUEST,
            "The association that owns this gear asks for a note with a loss report, so nothing was reported"),

    /** A loss reported without the document the owning association asks for. */
    LOSS_REPORT_NEEDS_A_DOCUMENT(
            163,
            HttpStatus.BAD_REQUEST,
            "The association that owns this gear asks for a document with a loss report, so nothing was reported"),

    /** A line of a stock-taking that names a size the inventory does not have. */
    INTAKE_SIZE_NOT_IN_INVENTORY(
            164, HttpStatus.BAD_REQUEST, "A line names a size this inventory does not have, so nothing was taken in"),

    /** A line of a stock-taking that names a member who is not at the station. */
    INTAKE_MEMBER_NOT_AT_STATION(
            165, HttpStatus.BAD_REQUEST, "A line names a member who is not at this station, so nothing was taken in"),

    /** A stock-taking that gives the same number to two of its lines. */
    INTAKE_NUMBER_TWICE(166, HttpStatus.BAD_REQUEST, "A number appears twice in the list, so nothing was taken in"),

    /** A line of a stock-taking whose number is already on another piece of gear. */
    INTAKE_NUMBER_TAKEN(
            167,
            HttpStatus.BAD_REQUEST,
            "A number in the list is already on another piece of gear, so nothing was taken in"),

    /** The kinds sharing a key across stations, asked for a kind that is not here. */
    ART_NOT_HERE_TO_MATCH(168, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind created under a name the inventory already has. */
    ART_NAME_TAKEN_ON_CREATE(169, HttpStatus.BAD_REQUEST, Sentences.ART_NAME_TAKEN),

    /** A kind changed to a name another kind of the inventory already has. */
    ART_NAME_TAKEN_ON_CHANGE(170, HttpStatus.BAD_REQUEST, Sentences.ART_NAME_TAKEN),

    /** A kind changed that is not here. */
    ART_NOT_HERE_TO_CHANGE(171, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** Kinds written for an inventory that is not here. */
    ART_INVENTORY_NOT_HERE(172, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** Kinds written for an inventory of one thing in many copies, which is ordered by its sizes instead. */
    ART_INVENTORY_UNIFORM(
            173,
            HttpStatus.BAD_REQUEST,
            "Only an inventory of different things has kinds, and this one holds one thing in many copies"),

    /** A kind created or changed with no name. */
    ART_NEEDS_A_NAME(174, HttpStatus.BAD_REQUEST, "A kind needs a name, so nothing was saved"),

    /** A kind assigned or merged that is not here. */
    ART_NOT_HERE(175, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind assigned or merged within an inventory it does not belong to. */
    ART_IN_ANOTHER_INVENTORY(
            176, HttpStatus.BAD_REQUEST, "That kind belongs to another inventory, so nothing was changed"),

    /** Gear given a kind or merged that is not here. */
    ART_PIECE_NOT_HERE(177, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** Gear given a kind of an inventory it does not belong to. */
    ART_PIECE_IN_ANOTHER_INVENTORY(
            178, HttpStatus.BAD_REQUEST, "That piece of gear belongs to another inventory, so nothing was changed"),

    /** A colour for a kind or piece of gear that is not written as a hash and six hexadecimal digits. */
    GLYPH_COLOUR_NOT_READABLE(
            179,
            HttpStatus.BAD_REQUEST,
            "A colour is written as # followed by six digits or letters from a to f, so nothing was saved"),

    /** A tag created at a station with a name that is blank once trimmed. */
    INVENTORY_TAG_NAME_MISSING_ON_CREATE(180, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag renamed to a name that is blank once trimmed. */
    INVENTORY_TAG_NAME_MISSING_ON_CHANGE(181, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag renamed onto the name another tag of the station already carries. */
    INVENTORY_TAG_NAME_TAKEN(
            182, HttpStatus.BAD_REQUEST, "The station already has a tag of that name, so nothing was saved"),

    /** A tag that went between being renamed and being read back. */
    INVENTORY_TAG_NOT_HERE_AFTER_CHANGE(
            183, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /**
     * A tag renamed that is gone or belongs to another station. One code for both, so the answer
     * does not say that a tag exists elsewhere.
     */
    INVENTORY_TAG_NOT_HERE_ON_CHANGE(184, HttpStatus.NOT_FOUND, Sentences.INVENTORY_TAG_NOT_HERE),

    /**
     * A tag deleted that is gone or belongs to another station. One code for both, so the answer
     * does not say that a tag exists elsewhere.
     */
    INVENTORY_TAG_NOT_HERE_ON_DELETE(185, HttpStatus.NOT_FOUND, Sentences.INVENTORY_TAG_NOT_HERE),

    /**
     * A piece of gear whose tags were asked for that is gone, sits in an inventory that is gone, or
     * belongs to another station. One code for all three, so the answer does not say it exists
     * elsewhere.
     */
    INVENTORY_TAG_ITEM_NOT_HERE_ON_READ(186, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /**
     * A piece of gear given new tags that is gone, sits in an inventory that is gone, or belongs to
     * another station. One code for all three, so the answer does not say it exists elsewhere.
     */
    INVENTORY_TAG_ITEM_NOT_HERE_ON_TAGGING(187, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /**
     * An inventory whose tags were asked for that is gone or belongs to another station. One code
     * for both, so the answer does not say it exists elsewhere.
     */
    INVENTORY_TAG_INVENTORY_NOT_HERE(188, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A self-check answer taken as it stands that first needs the member's record putting right. */
    SELF_CHECK_REVIEW_RECORD_NEEDS_PUTTING_RIGHT(
            189,
            HttpStatus.BAD_REQUEST,
            "This answer needs the record putting right before it can be taken, so nothing was changed"),

    /** A self-check answer somebody else settled between being read and being taken. */
    SELF_CHECK_REVIEW_SETTLED_BEFORE_TAKING(190, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A record put right for a self-check answer that does not say the record is wrong. */
    SELF_CHECK_REVIEW_NOTHING_TO_PUT_RIGHT(
            191,
            HttpStatus.BAD_REQUEST,
            "This answer does not ask for the record to be put right, so nothing was changed"),

    /** A self-check answer somebody else settled while its record was being put right. */
    SELF_CHECK_REVIEW_SETTLED_BEFORE_CORRECTING(192, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A self-check answer sent back to the member without a reason. */
    SELF_CHECK_REVIEW_REASON_MISSING(
            193, HttpStatus.BAD_REQUEST, "Say why the answer cannot be settled, so nothing was sent back"),

    /** A self-check answer somebody else settled before it could be sent back. */
    SELF_CHECK_REVIEW_SETTLED_BEFORE_SENDING_BACK(
            194, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A record put right for a self-check answer without saying what the member actually holds. */
    SELF_CHECK_REVIEW_CORRECTION_MISSING(
            195, HttpStatus.BAD_REQUEST, "Say what the member is actually holding, so nothing was changed"),

    /**
     * A self-check submission about the reviewer's own gear, which they may not sign off. Also the
     * reason the review screen gives for not offering the sign-off.
     */
    SELF_CHECK_REVIEW_OF_OWN_GEAR(196, HttpStatus.FORBIDDEN, "This submission is about your own gear"),

    /**
     * A self-check submission the reviewer handed in themselves, which they may not sign off. Also
     * the reason the review screen gives for not offering the sign-off.
     */
    SELF_CHECK_REVIEW_OF_OWN_SUBMISSION(197, HttpStatus.FORBIDDEN, "You entered this submission yourself"),

    /** A self-check answer the reviewer entered themselves, which they may not settle. */
    SELF_CHECK_REVIEW_OF_OWN_ANSWER(198, HttpStatus.FORBIDDEN, "You entered this answer yourself"),

    /**
     * A self-check under review that is gone or belongs to another station. One code for both, so
     * the answer does not say that it exists elsewhere.
     */
    SELF_CHECK_REVIEW_TASK_NOT_HERE(199, HttpStatus.NOT_FOUND, Sentences.SELF_CHECK_NOT_HERE),

    /**
     * A self-check answer under review that is gone or belongs to another submission. One code for
     * both, so the answer does not say that it exists elsewhere.
     */
    SELF_CHECK_REVIEW_ANSWER_NOT_HERE(200, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** A self-check answer settled on a submission that has not been handed in, or is finished. */
    SELF_CHECK_REVIEW_TASK_NOT_WAITING(201, HttpStatus.CONFLICT, "This self-check is not waiting to be read"),

    /** A self-check answer settled that was already settled when it was opened for settling. */
    SELF_CHECK_REVIEW_ANSWER_ALREADY_SETTLED(202, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A check of a member's gear begun while another checker already holds that member. */
    INVENTORY_CHECK_MEMBER_ALREADY_LOCKED(203, HttpStatus.CONFLICT, Sentences.INVENTORY_CHECK_MEMBER_TAKEN),

    /** A check of a member's gear begun at the moment another checker took the same member. */
    INVENTORY_CHECK_MEMBER_LOCKED_MEANWHILE(204, HttpStatus.CONFLICT, Sentences.INVENTORY_CHECK_MEMBER_TAKEN),

    /** A record put right against an inventory that is gone. */
    INVENTORY_CHECK_CORRECTION_INVENTORY_NOT_HERE(205, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A record put right with a new piece in an inventory of both owners, naming neither. */
    INVENTORY_CHECK_CORRECTION_OWNER_MISSING(
            206, HttpStatus.BAD_REQUEST, "This inventory holds gear of both owners, so say whose the new piece is"),

    /** A record put right with a new piece written down as a partner station's. */
    INVENTORY_CHECK_CORRECTION_OWNED_BY_PARTNER(
            207,
            HttpStatus.BAD_REQUEST,
            "A new piece cannot be written down as a partner station's, so nothing was changed"),

    /** A free piece picked to put a record right that is gone. */
    INVENTORY_CHECK_PICKED_ITEM_NOT_HERE(208, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A free piece picked to put a record right that sits in another inventory. */
    INVENTORY_CHECK_PICKED_ITEM_IN_OTHER_INVENTORY(
            209, HttpStatus.BAD_REQUEST, "That piece of gear sits in another inventory, so nothing was changed"),

    /** A piece picked to put a record right that somebody already holds. */
    INVENTORY_CHECK_PICKED_ITEM_TAKEN(
            210, HttpStatus.BAD_REQUEST, "That piece of gear is already with somebody, so nothing was changed"),

    /** The piece a correction takes off a member's record, gone by the time it is taken off. */
    INVENTORY_CHECK_REPLACED_ITEM_NOT_HERE(211, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** The piece a correction takes off a member's record, which is not on that member's record. */
    INVENTORY_CHECK_REPLACED_ITEM_NOT_THE_MEMBERS(212, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_ON_MEMBERS_RECORD),

    /** Self-checks handed out without naming a single member. */
    SELF_CHECK_NO_MEMBER_NAMED(
            213, HttpStatus.BAD_REQUEST, "Name at least one member to ask, so nothing was handed out"),

    /** A self-check handed to a member who is not of this station. */
    SELF_CHECK_MEMBER_NOT_OF_STATION(214, HttpStatus.BAD_REQUEST, "One of those members is not of this station"),

    /** A self-check handed to a member who has left the station. */
    SELF_CHECK_FOR_FORMER_MEMBER(215, HttpStatus.BAD_REQUEST, "A former member cannot be asked to check their gear"),

    /** Self-check answers saved with nothing in them. */
    SELF_CHECK_NOTHING_SAID(216, HttpStatus.BAD_REQUEST, "Give at least one answer, so nothing was saved"),

    /** A self-check handed in that was handed in at the same moment from somewhere else. */
    SELF_CHECK_ALREADY_HANDED_IN(217, HttpStatus.CONFLICT, "This self-check has already been handed in"),

    /** A report held back on a self-check about a piece of gear that is gone. */
    SELF_CHECK_HELD_BACK_ITEM_NOT_HERE(218, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A report held back on a self-check about a piece that is not on the member's record. */
    SELF_CHECK_HELD_BACK_ITEM_NOT_THE_MEMBERS(219, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_ON_MEMBERS_RECORD),

    /** A loss held back on a self-check about gear borrowed from a partner station. */
    SELF_CHECK_HELD_BACK_LOSS_OF_BORROWED_GEAR(220, HttpStatus.BAD_REQUEST, Sentences.BORROWED_GEAR_LOST_AT_PARTNER),

    /** A report held back on a self-check before the size the member holds has been saved. */
    SELF_CHECK_HELD_BACK_WITHOUT_SAVED_SIZE(
            221,
            HttpStatus.BAD_REQUEST,
            "Save the size you are actually holding before reporting anything about this piece"),

    /** A second report of the same kind held back on a self-check about the same piece. */
    SELF_CHECK_HELD_BACK_ALREADY_REPORTED(
            222, HttpStatus.BAD_REQUEST, "This has already been reported for this piece of gear, so nothing was saved"),

    /** A swap held back on a self-check for a piece whose inventory is gone. */
    SELF_CHECK_HELD_BACK_INVENTORY_NOT_HERE(223, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** A swap held back on a self-check for gear that is not kept by size. */
    SELF_CHECK_HELD_BACK_SWAP_WITHOUT_SIZES(224, HttpStatus.BAD_REQUEST, "This kind of gear is not swapped by size"),

    /** A swap held back on a self-check asking for a size the gear does not come in. */
    SELF_CHECK_HELD_BACK_SIZE_NOT_OFFERED(225, HttpStatus.BAD_REQUEST, Sentences.SELF_CHECK_SIZE_NOT_OFFERED),

    /**
     * A self-check that is gone or belongs to another station, opened by the member answering it.
     * One code for both, so the answer does not say that it exists elsewhere.
     */
    SELF_CHECK_NOT_HERE(226, HttpStatus.NOT_FOUND, Sentences.SELF_CHECK_NOT_HERE),

    /** A self-check of a member the caller does not answer for. */
    SELF_CHECK_NOT_YOURS_TO_ANSWER(
            227, HttpStatus.FORBIDDEN, "This self-check belongs to somebody you do not answer for"),

    /** A self-check answered or handed in after it was handed in or closed. */
    SELF_CHECK_CLOSED(228, HttpStatus.CONFLICT, "This self-check no longer takes answers, so nothing was saved"),

    /** A self-check answer that says nothing at all. */
    SELF_CHECK_ANSWER_EMPTY(229, HttpStatus.BAD_REQUEST, "Every answer has to say something, so nothing was saved"),

    /** A self-check answer meant for an empty place, given about a piece of gear. */
    SELF_CHECK_PLACE_ANSWER_ON_A_PIECE(
            230, HttpStatus.BAD_REQUEST, "That answer is about an empty place, not about a piece of gear"),

    /** A self-check answer about a piece of gear that is gone. */
    SELF_CHECK_ANSWERED_ITEM_NOT_HERE(231, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A self-check answer about a piece that is not on the member's record. */
    SELF_CHECK_ANSWERED_ITEM_NOT_THE_MEMBERS(232, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_ON_MEMBERS_RECORD),

    /** A self-check answer saying the station's own gear is missing, which belongs with the losses. */
    SELF_CHECK_OWN_GEAR_MISSING_NOT_REPORTED_HERE(
            233, HttpStatus.BAD_REQUEST, "Report the station's own gear as missing where losses are reported"),

    /** A self-check answer saying a piece turned up that nobody reported missing. */
    SELF_CHECK_TURNED_UP_BUT_NOT_MISSING(
            234, HttpStatus.BAD_REQUEST, "This piece of gear is not recorded as missing, so it cannot have turned up"),

    /** A self-check answer meant for a piece of gear, given without naming one. */
    SELF_CHECK_PIECE_ANSWER_WITHOUT_PIECE(
            235, HttpStatus.BAD_REQUEST, "That answer is about a piece of gear, and no piece was named"),

    /** A self-check answer about an empty place that does not say which place. */
    SELF_CHECK_PLACE_NOT_NAMED(236, HttpStatus.BAD_REQUEST, "An answer about an empty place has to say which one"),

    /** A self-check answer about gear of a kind the member is not asked to hold. */
    SELF_CHECK_KIND_NOT_ASKED_OF_MEMBER(237, HttpStatus.BAD_REQUEST, "Nothing of this kind is asked of this member"),

    /** A self-check answer about an empty place the member does not have. */
    SELF_CHECK_PLACE_NOT_THERE(238, HttpStatus.BAD_REQUEST, "This member has no such empty place"),

    /** A number typed on a self-check answer that is not about something the member holds. */
    SELF_CHECK_NUMBER_ON_WRONG_ANSWER(
            239, HttpStatus.BAD_REQUEST, "Only a place you are holding something for takes a number"),

    /** A size given on a self-check answer that does not say what the member actually holds. */
    SELF_CHECK_SIZE_ON_WRONG_ANSWER(
            240, HttpStatus.BAD_REQUEST, "Only an answer that says what the member actually holds takes a size"),

    /** A size given on a self-check answer that the gear does not come in. */
    SELF_CHECK_ANSWERED_SIZE_NOT_OFFERED(241, HttpStatus.BAD_REQUEST, Sentences.SELF_CHECK_SIZE_NOT_OFFERED),

    /** A size added to an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_SIZE(242, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A size added to an inventory that holds different things rather than one thing in many copies. */
    INVENTORY_SIZE_ON_A_COLLECTION(
            243,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so it keeps no size list and nothing was saved"),

    /** A requirement written for an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_REQUIREMENT(244, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A requirement written for an inventory that holds different things. */
    INVENTORY_REQUIREMENT_ON_A_COLLECTION(
            245,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so a requirement does not apply to it and nothing was saved"),

    /** More of something ordered for an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_ORDER(246, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** More of something ordered for an inventory that holds different things. */
    INVENTORY_ORDER_ON_A_COLLECTION(
            247,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so more of it cannot be ordered and nothing was saved"),

    /** The shelf for borrowed gear deleted while something is still on it. */
    INVENTORY_BORROWED_SHELF_NOT_EMPTY(
            248,
            HttpStatus.BAD_REQUEST,
            "This shelf still holds gear belonging to somebody else, so it was not deleted"),

    /** A piece of gear written straight onto the shelf for borrowed gear. */
    INVENTORY_BORROWED_SHELF_ON_CREATE(249, HttpStatus.BAD_REQUEST, Sentences.BORROWED_SHELF_ONLY_BORROWED),

    /** A piece of gear with a named owner written straight onto the shelf for borrowed gear. */
    INVENTORY_BORROWED_SHELF_ON_OWNED_CREATE(250, HttpStatus.BAD_REQUEST, Sentences.BORROWED_SHELF_ONLY_BORROWED),

    /** A piece of gear moved onto the shelf for borrowed gear. */
    INVENTORY_BORROWED_SHELF_ON_MOVE(251, HttpStatus.BAD_REQUEST, Sentences.BORROWED_SHELF_ONLY_BORROWED),

    /** A piece of gear written down by hand as a partner station's. */
    INVENTORY_PARTNER_GEAR_BY_HAND(
            252,
            HttpStatus.BAD_REQUEST,
            "Gear belonging to a partner station arrives by handover, not by hand, so nothing was saved"),

    /** A piece of gear created with a kind that belongs to another inventory. */
    INVENTORY_ITEM_KIND_ELSEWHERE_ON_CREATE(253, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_KIND_ELSEWHERE),

    /** A piece of gear changed to a kind that belongs to another inventory. */
    INVENTORY_ITEM_KIND_ELSEWHERE_ON_CHANGE(254, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_KIND_ELSEWHERE),

    /** A piece of gear created in an inventory that is gone, found while checking its owner. */
    INVENTORY_NOT_HERE_FOR_OWNER_CHECK(255, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** The station's own gear written into an inventory that only holds gear of the body above it. */
    INVENTORY_HOLDS_NO_STATION_GEAR(
            256, HttpStatus.BAD_REQUEST, "This inventory does not hold the station's own gear, so nothing was saved"),

    /** Gear of the body above the station written into an inventory that only holds the station's own. */
    INVENTORY_HOLDS_NO_ASSOCIATION_GEAR(
            257,
            HttpStatus.BAD_REQUEST,
            "This inventory does not hold gear owned by the association, so nothing was saved"),

    /** A requirement naming a group of stations for an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_REQUIREMENT_GROUP(258, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A requirement naming a group of stations that is gone. */
    INVENTORY_REQUIREMENT_GROUP_NOT_HERE(
            259, HttpStatus.BAD_REQUEST, "That group of stations is not here any more, so nothing was saved"),

    /** A requirement naming a group of stations filed by another association. */
    INVENTORY_REQUIREMENT_GROUP_OF_ANOTHER_ASSOCIATION(
            260,
            HttpStatus.BAD_REQUEST,
            "A requirement can only name a group of stations of the association that wrote it"),

    /** Gear recorded as an association's in an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_ASSOCIATION_CHECK(261, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** Gear recorded as belonging to an association the station does not answer to. */
    INVENTORY_GEAR_OF_ANOTHER_ASSOCIATION(
            262,
            HttpStatus.BAD_REQUEST,
            "Gear can only be recorded as belonging to the association this station answers to"),

    /** A value on a piece of gear that the field describing it does not take. */
    INVENTORY_ITEM_FIELD_VALUE_NOT_ACCEPTED(
            263, HttpStatus.BAD_REQUEST, "That value does not suit this field, so nothing was saved"),

    /** A piece of gear moved that went before the move reached it. */
    INVENTORY_ITEM_NOT_HERE_TO_MOVE(264, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear moved into an inventory that is gone. */
    INVENTORY_MOVE_TARGET_NOT_HERE(265, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A piece of gear moved out of an inventory that is gone. */
    INVENTORY_MOVE_SOURCE_NOT_HERE(266, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** A piece of gear moved into an inventory of another station. */
    INVENTORY_MOVE_TO_ANOTHER_STATION(
            267, HttpStatus.BAD_REQUEST, "A piece of gear can only be moved into an inventory of the same station"),

    /** A new piece made and handed out from an inventory that is gone. */
    INVENTORY_NOT_HERE_TO_HAND_OUT_NEW(268, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** Gear borrowed from a partner station, described anew by the station holding it. */
    INVENTORY_PARTNER_GEAR_NOT_YOURS_TO_DESCRIBE(
            269, HttpStatus.FORBIDDEN, "This gear belongs to a partner station and can only be described by them"),

    /** Gear borrowed from a partner station, moved by the station holding it. */
    INVENTORY_PARTNER_GEAR_NOT_YOURS_TO_MOVE(
            270, HttpStatus.FORBIDDEN, "This gear belongs to a partner station and can only be moved by them"),

    /** Gear borrowed from a partner station, deleted by the station holding it. */
    INVENTORY_PARTNER_GEAR_NOT_YOURS_TO_DELETE(
            271, HttpStatus.FORBIDDEN, "This gear belongs to a partner station and can only be deleted by them"),

    /** Gear of the body above the station, described anew by somebody other than that body. */
    INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DESCRIBE(
            272,
            HttpStatus.FORBIDDEN,
            "This gear belongs to the body above the station and can only be described by them"),

    /** Gear of the body above the station, moved by somebody other than that body. */
    INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_MOVE(
            273, HttpStatus.FORBIDDEN, "This gear belongs to the body above the station and can only be moved by them"),

    /** Gear of the body above the station, deleted by somebody other than that body. */
    INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DELETE(
            274,
            HttpStatus.FORBIDDEN,
            "This gear belongs to the body above the station and can only be deleted by them");

    private final Definition definition;

    InventoryRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.INVENTORY, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
