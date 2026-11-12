/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.security.RouteRole;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A permission represents the permission to perform an action or a set of actions.
 * A permission might contain other permissions, which are implicitly granted.
 *
 * <p>A permission of a module names it. While that module is switched off at a station, the permission is
 * kept wherever it was granted but grants nothing there, and the permission picker does not offer it.
 * Permissions of the station itself and of its members name no module.
 */
public enum StationPermission implements RouteRole {

    /**
     * The general user permission. Grants access to most things.
     */
    USER,

    /**
     * Allows the member to log in on the station.
     */
    LOGIN(USER),

    /**
     * Allows reading past attendance data
     */
    ATTENDANCE_READ(StationModule.ATTENDANCE),

    /**
     * Allows creating a new attendance session, editing and register attendance
     */
    ATTENDANCE_EDIT(StationModule.ATTENDANCE, ATTENDANCE_READ),

    /**
     * Allows configuring the attendance templates
     */
    ATTENDANCE_CONFIGURE(StationModule.ATTENDANCE),

    /**
     * Allows exporting attendance data
     */
    ATTENDANCE_EXPORT(StationModule.ATTENDANCE, ATTENDANCE_READ),

    /**
     * Grants access to all attendance actions.
     */
    ATTENDANCE_MANAGER(
            StationModule.ATTENDANCE, ATTENDANCE_EDIT, ATTENDANCE_CONFIGURE, ATTENDANCE_EXPORT, ATTENDANCE_READ),

    /**
     * Allows creating new inventory items of type {@link InventoryType#EXTERNAL}
     */
    INVENTORY_CREATE_EXTERNAL(StationModule.INVENTORY),

    /**
     * Allows creating new inventory items of type {@link InventoryType#INTERNAL}
     */
    INVENTORY_CREATE_INTERNAL(StationModule.INVENTORY),

    /**
     * Allows reading all inventory data of users
     */
    INVENTORY_READ(StationModule.INVENTORY),

    /**
     * Allows creating new inventory items
     */
    INVENTORY_CREATE(StationModule.INVENTORY, INVENTORY_CREATE_EXTERNAL, INVENTORY_CREATE_INTERNAL, INVENTORY_READ),

    /**
     * Allows managing the inventory exchanges.
     */
    INVENTORY_MOVEMENTS(StationModule.INVENTORY_MOVEMENTS, INVENTORY_CREATE_EXTERNAL, INVENTORY_READ),

    /**
     * Allows managing inventory procurements.
     * Only allows creating. To mark as done the {@link #ATTENDANCE_EDIT} permission is required for the matching inventory type
     */
    INVENTORY_PROCUREMENT(StationModule.INVENTORY_PROCUREMENT),

    /**
     * Allows performing an inventory check on a user and reviewing the checks members did themselves.
     *
     * <p>Belongs to the inventory as a whole rather than to one of its parts, because it serves both kinds
     * of check: switching off the station's checks leaves the review of self-checks to whoever holds it.
     */
    INVENTORY_CHECK(StationModule.INVENTORY, INVENTORY_CREATE_EXTERNAL),

    /**
     * Allows creating new inventory lending requests and managing outgoing requests.
     */
    INVENTORY_LENDING_REQUEST(StationModule.INVENTORY_LENDING),

    /**
     * Allows managing inventory lending requests
     */
    INVENTORY_LENDING_MANAGER(StationModule.INVENTORY_LENDING, INVENTORY_LENDING_REQUEST),

    /**
     * Allows editing all inventory data of users
     */
    INVENTORY_EDIT(StationModule.INVENTORY, INVENTORY_CREATE),

    /**
     * Allows assigning items to members and accepting returns. Off by default
     * for every role; the station owner grants it explicitly.
     */
    INVENTORY_ASSIGN(StationModule.INVENTORY, INVENTORY_READ),

    /**
     * Allows creating, renaming, moving and deleting storage containers, and
     * placing items into containers.
     */
    INVENTORY_STORAGE(StationModule.INVENTORY, INVENTORY_READ),

    /**
     * Allows managing inventory items and lending requests.
     */
    INVENTORY_MANAGER(
            StationModule.INVENTORY,
            INVENTORY_ASSIGN,
            INVENTORY_CHECK,
            INVENTORY_CREATE,
            INVENTORY_EDIT,
            INVENTORY_MOVEMENTS,
            INVENTORY_LENDING_MANAGER,
            INVENTORY_PROCUREMENT,
            INVENTORY_READ,
            INVENTORY_STORAGE),

    /**
     * Allows managing event templates
     */
    EVENT_MANAGE_TEMPLATE(StationModule.EVENTS),

    /**
     * Allows managing event categories
     */
    EVENT_MANAGE_CATEGORY(StationModule.EVENTS),

    /**
     * Allows reading the internal side of an event without being able to change anything: the
     * material it needs and the files kept back from the room.
     *
     * <p>Whoever runs an appointment needs to read what it takes to run it, and that is not the same
     * as being allowed to write the appointment or to keep the station's equipment. Granted by
     * {@link #EVENT_EDIT}, because somebody who may write an event can obviously read it.
     */
    EVENT_INTERNAL(StationModule.EVENTS),

    /**
     * Allows creating and editing events
     */
    EVENT_EDIT(StationModule.EVENTS, EVENT_INTERNAL),

    /**
     * Allows confirming and deny registrations.
     */
    EVENT_REGISTRATION(StationModule.EVENTS),

    /**
     * Allows federating events to other stations
     */
    EVENTS_FEDERATE(StationModule.EVENTS, EVENT_EDIT),

    /**
     * Allows managing events
     */
    EVENT_MANAGER(
            StationModule.EVENTS, EVENTS_FEDERATE, EVENT_REGISTRATION, EVENT_MANAGE_TEMPLATE, EVENT_MANAGE_CATEGORY),

    /**
     * Allows reading the station's document store.
     *
     * <p>Standing alone this reaches only the documents that name nobody: a contract, a test
     * certificate, the paperwork a station keeps about itself. Reaching a document that names a member
     * is {@link #DOCUMENT_READ_MEMBER}, so the equipment officer can be given the store without being
     * given everybody's paperwork along with it.
     */
    DOCUMENT_READ(StationModule.DOCUMENTS),

    /**
     * Allows reading the documents that name a member.
     *
     * <p>Implies {@link #DOCUMENT_READ}: whoever may read the paperwork of a person may read the
     * paperwork of the station.
     */
    DOCUMENT_READ_MEMBER(StationModule.DOCUMENTS, DOCUMENT_READ),

    /**
     * Allows filing documents into the store and editing what is there.
     */
    DOCUMENT_EDIT(StationModule.DOCUMENTS, DOCUMENT_READ),

    /**
     * Allows saying which members a document names, and everything that follows from it.
     *
     * <p>Implies {@link #DOCUMENT_READ_MEMBER}, and that is what keeps the store honest rather than a
     * convenience. Whether a document names a member decides who may read it, so a permission to
     * change that answer without being allowed to read the answer would let its holder unbind a
     * document they may not see and read it on the next request.
     */
    DOCUMENT_EDIT_MEMBER(StationModule.DOCUMENTS, DOCUMENT_READ_MEMBER, DOCUMENT_EDIT),

    /**
     * Allows creating, changing and archiving the templates documents are generated from.
     *
     * <p>A template holds no member data of its own, so this reaches no member. Generating a document
     * for somebody, and looking at one rendered for them, is {@link #DOCUMENT_EDIT_MEMBER}.
     */
    DOCUMENT_TEMPLATE_EDIT(StationModule.DOCUMENTS),

    /**
     * Allows collecting signatures in person, as a witness: the holder picks a person of the station and a
     * document for them, hands their own device over, and confirms with their own account that this person
     * signed in front of them.
     *
     * <p>It implies no other permission. The holder reads the documents of other people only while they
     * collect a signature on them, and never sees the rest of anybody's paperwork. No kind of member holds
     * it by default; a station grants it on purpose.
     */
    DOCUMENT_SIGN_OTHERS(StationModule.DOCUMENTS),

    /**
     * Allows everything the document store has to offer.
     */
    DOCUMENT_MANAGER(StationModule.DOCUMENTS, DOCUMENT_EDIT_MEMBER, DOCUMENT_TEMPLATE_EDIT, DOCUMENT_SIGN_OTHERS),

    /**
     * Allows reading all member data.
     *
     * <p>Implies {@link #DOCUMENT_READ_MEMBER}, because reading member paperwork is what this
     * permission bought before documents had permissions of their own, and taking that away from a
     * role that holds only this would be a regression nobody asked for.
     */
    MEMBER_READ(DOCUMENT_READ_MEMBER),

    /**
     * Allows creating and editing member notes.
     * Implicit access to {@link #MEMBER_READ}
     */
    MEMBER_NOTES(MEMBER_READ),

    /**
     * Allows managing member data of linked members.
     */
    MEMBER_GUARDIAN,

    /**
     * Allows to get notified about changes to member data and confirming them.
     * Implicit access to {@link #MEMBER_READ}
     */
    MEMBER_CHANGES(MEMBER_READ),

    /**
     * Allows editing and creating groups.
     */
    MEMBER_MANAGE_GROUP,
    /**
     * Allows editing and creating tags.
     */
    MEMBER_MANAGE_TAGS,

    /**
     * Allows putting documents on one's own profile. Reading them needs no permission at all: they
     * are the member's own. Putting them on somebody else's profile is {@link #MEMBER_EDIT}.
     */
    MEMBER_SELF_UPLOAD,

    /**
     * Allows editing and create members.
     */
    MEMBER_EDIT(MEMBER_READ, MEMBER_SELF_UPLOAD, DOCUMENT_EDIT_MEMBER),

    /**
     * Allows configuring the member fields config.
     */
    MEMBER_FIELDS,

    /**
     * Allows exporting member data
     */
    MEMBER_EXPORT(MEMBER_READ),

    /**
     * Allows managing members
     */
    MEMBER_MANAGER(
            MEMBER_CHANGES,
            MEMBER_EDIT,
            MEMBER_EXPORT,
            MEMBER_FIELDS,
            MEMBER_MANAGE_GROUP,
            MEMBER_MANAGE_TAGS,
            MEMBER_NOTES),

    /**
     * Allows reading the member waitlist.
     */
    WAITLIST_READ(StationModule.WAITING_LIST),

    /**
     * Allows adding entries to the member waitlist.
     */
    WAITLIST_ADD(StationModule.WAITING_LIST, WAITLIST_READ),

    /**
     * Allows editing and managing entries on the member waitlist.
     */
    WAITLIST_EDIT(StationModule.WAITING_LIST, WAITLIST_ADD),

    /**
     * Allows managing the member waitlist.
     */
    WAITLIST_MANAGER(StationModule.WAITING_LIST, WAITLIST_EDIT),

    /**
     * Allows creating news entries
     */
    NEWS_EDIT(StationModule.NEWS),

    /**
     * Allows federating news entries to other stations
     */
    NEWS_FEDERATE(StationModule.NEWS),

    /**
     * Allows managing news entries
     */
    NEWS_MANAGER(StationModule.NEWS, NEWS_EDIT, NEWS_FEDERATE),

    /**
     * Allows reading poll results
     */
    POLL_VIEW_RESULTS(StationModule.FORMS),

    /**
     * Allows creating polls and forms
     */
    POLL_CREATE(StationModule.FORMS, POLL_VIEW_RESULTS),

    /**
     * Allows managing polls and forms
     */
    POLL_MANAGER(StationModule.FORMS, POLL_CREATE),

    /**
     * Allows creating lost and found items
     */
    LOST_AND_FOUND_CREATE(StationModule.LOST_AND_FOUND),

    /**
     * Allows managing lost and found items
     */
    LOST_AND_FOUND_MANAGE(StationModule.LOST_AND_FOUND, LOST_AND_FOUND_CREATE),

    /**
     * Allows managing lost and found items
     */
    LOST_AND_FOUND_MANAGER(StationModule.LOST_AND_FOUND, LOST_AND_FOUND_MANAGE),

    /**
     * Allows read-only access to checklists: listing them, opening the detail view,
     * filtering rows and viewing note history. No write access.
     */
    CHECKLIST_READ,

    /**
     * Allows creating, editing, refreshing and deleting checklists, editing their columns,
     * ticking cells, adding or removing rows by hand.
     */
    CHECKLIST_MANAGE(CHECKLIST_READ),

    /**
     * Allows managing checklists.
     */
    CHECKLIST_MANAGER(CHECKLIST_MANAGE),
    /**
     * Allows viewing quiz catalogs
     */
    TEST_CATALOG_VIEW(StationModule.QUIZ),
    /**
     * Allows editing quizzes
     */
    TEST_CATALOG_EDIT(StationModule.QUIZ, TEST_CATALOG_VIEW),
    /**
     * Allows configuring new quizzes
     */
    TEST_CONFIGURE(StationModule.QUIZ, TEST_CATALOG_EDIT),

    /**
     * Allows reading the test results of users
     */
    TEST_RESULT_READ(StationModule.QUIZ),

    /**
     * Allows reviewing quizzed/grading them
     */
    TEST_REVIEW(StationModule.QUIZ, TEST_RESULT_READ),

    /**
     * Allows sharing the station's quiz catalogs with federation partners
     */
    TEST_CATALOG_SHARE(StationModule.QUIZ),

    /**
     * Allows managing tests
     */
    TEST_MANAGER(StationModule.QUIZ, TEST_CONFIGURE, TEST_REVIEW, TEST_CATALOG_SHARE),

    /**
     * Allows filling out a protocol
     */
    PROTOCOL_TESTER(StationModule.TEST_PROTOCOL),
    /**
     * Allows creating a new protocol run
     */
    PROTOCOL_CREATE(StationModule.TEST_PROTOCOL, PROTOCOL_TESTER),
    /**
     * Allows configuring protocols
     */
    PROTOCOL_CONFIGURE(StationModule.TEST_PROTOCOL, PROTOCOL_CREATE),
    /**
     * Allows sharing the station's protocols with federation partners
     */
    PROTOCOL_SHARE(StationModule.TEST_PROTOCOL),

    PROTOCOL_MANAGER(StationModule.TEST_PROTOCOL, PROTOCOL_CONFIGURE, PROTOCOL_SHARE),

    /**
     * Allows generally using boards.
     */
    BOARD_USE(StationModule.BOARDS),

    /**
     * Allows editing and creating boards
     */
    BOARD_EDIT(StationModule.BOARDS, BOARD_USE),

    /**
     * Allows editing and creating boards
     */
    BOARD_FEDERATE(StationModule.BOARDS, BOARD_EDIT),

    BOARD_MANAGER(StationModule.BOARDS, BOARD_FEDERATE),

    /**
     * Allows creating and editing public pages.
     */
    PAGE_EDIT,

    /**
     * Allows viewing the submissions of CONTACT forms embedded in pages (via the FORMS_CTA cell)
     * and acknowledging them on behalf of the station.
     */
    PAGE_FORMS_VIEW,

    /**
     * Allows viewing the analytics + individual responses of POLL forms embedded in pages (via
     * the POLL_EMBED cell).
     */
    PAGE_POLLS_VIEW,

    /**
     * Allows publishing/unpublishing and deleting public pages, and setting the landing page.
     */
    PAGE_MANAGER(PAGE_EDIT, PAGE_FORMS_VIEW, PAGE_POLLS_VIEW),

    /**
     * Allows reading procedures assigned to the member.
     */
    PROCEDURE_READ(StationModule.PROCEDURES),

    /**
     * Allows creating, assigning, and progressing procedures.
     */
    PROCEDURE_EDIT(StationModule.PROCEDURES, PROCEDURE_READ),

    /**
     * Allows creating and managing procedure templates.
     */
    PROCEDURE_MANAGER(StationModule.PROCEDURES, PROCEDURE_EDIT),

    /**
     * Knowledgebase management
     */
    KNOWLEDGE_EDIT(StationModule.KNOWLEDGE_BASE),

    /**
     * Allows changing federation settings of the knowledgebase.
     */
    KNOWLEDGE_FEDERATE(StationModule.KNOWLEDGE_BASE, KNOWLEDGE_EDIT),

    /**
     * Knowledgebase management
     */
    KNOWLEDGE_MANAGER(StationModule.KNOWLEDGE_BASE, KNOWLEDGE_EDIT, KNOWLEDGE_FEDERATE),

    /**
     * Allows editing the stations look and feel.
     */
    STATION_LOOK_AND_FEEL,

    /**
     * Allows configuring general station settings.
     */
    STATION_GENERAL,

    /**
     * Allows managing the mail settings
     */
    STATION_MAIL,

    /**
     * Allows configuring federation settings of the station.
     */
    STATION_FEDERATION,
    /**
     * Allows configuring enabled modules.
     */
    STATION_MODULES,

    /**
     * Allows configuring the import and export of the station.
     */
    STATION_IMPORT_EXPORT,

    /**
     * Allows accessing the station statistics
     */
    STATION_STATISTICS,

    /**
     * Allows managing the station.
     */
    STATION_MANAGER(
            STATION_FEDERATION,
            STATION_GENERAL,
            STATION_IMPORT_EXPORT,
            STATION_LOOK_AND_FEEL,
            STATION_MAIL,
            STATION_MODULES,
            STATION_STATISTICS),

    /**
     * The station administrator permission. Grants access to all station management features.
     */
    STATION_ADMINISTRATOR(
            ATTENDANCE_MANAGER,
            BOARD_MANAGER,
            CHECKLIST_MANAGER,
            DOCUMENT_MANAGER,
            EVENT_MANAGER,
            INVENTORY_MANAGER,
            KNOWLEDGE_MANAGER,
            LOGIN,
            LOST_AND_FOUND_MANAGER,
            MEMBER_MANAGER,
            NEWS_MANAGER,
            PAGE_MANAGER,
            POLL_MANAGER,
            PROCEDURE_MANAGER,
            PROTOCOL_MANAGER,
            STATION_MANAGER,
            TEST_MANAGER,
            WAITLIST_MANAGER);

    private final @Nullable StationModule module;
    private final StationPermission[] children;
    private volatile Set<StationPermission> allChildren;

    StationPermission(StationPermission... children) {
        this.module = null;
        this.children = children;
    }

    StationPermission(StationModule module, StationPermission... children) {
        this.module = module;
        this.children = children;
    }

    /**
     * Expands a set of permissions to include all transitively contained child permissions.
     */
    public static Set<StationPermission> expand(Set<StationPermission> permissions) {
        Set<StationPermission> expanded = EnumSet.copyOf(permissions);
        for (StationPermission permission : permissions) {
            expanded.addAll(permission.allChildren());
        }
        return expanded;
    }

    /**
     * Expands a set of permissions the way {@link #expand(Set)} does, but grants nothing through a
     * permission whose module is switched off.
     *
     * <p>Such a permission stays stored and is simply not held: neither it nor anything it would imply
     * through it ends up in the result. What another held permission implies on its own still counts, so
     * a station that switched its document store off takes the documents from a member manager and leaves
     * them every member right.
     *
     * @param permissions the permissions granted
     * @param disabled    the switched-off modules, with their parts
     * @return the permissions held
     */
    public static Set<StationPermission> expand(Set<StationPermission> permissions, Set<StationModule> disabled) {
        Set<StationPermission> expanded = EnumSet.noneOf(StationPermission.class);
        Deque<StationPermission> pending = new ArrayDeque<>(permissions);
        while (!pending.isEmpty()) {
            StationPermission permission = pending.pop();
            if (permission.isOffIn(disabled) || !expanded.add(permission)) continue;
            pending.addAll(List.of(permission.children));
        }
        return expanded;
    }

    /**
     * The module this permission belongs to.
     *
     * @return the module or part of one, empty for a permission of the station itself or its members
     */
    public Optional<StationModule> module() {
        return Optional.ofNullable(module);
    }

    /**
     * Whether this permission grants nothing because its module is switched off.
     *
     * @param disabled the switched-off modules, with or without their parts
     * @return {@code true} when the permission names a module that is off
     */
    public boolean isOffIn(Set<StationModule> disabled) {
        StationModule own = module;
        return own != null && own.isOffIn(disabled);
    }

    public StationPermission[] getChildren() {
        return children;
    }

    /**
     * Returns all permissions transitively included by this permission (direct and indirect children).
     *
     * <p>Gathered into a set of its own and only then kept. Filling the kept one in place handed
     * every other thread a half-built answer, and a permission set is copied out of this the moment
     * it is asked for: a session resolved during that window carried a manager's rights without
     * most of what a manager may do, and looked for all the world like rights taken away.
     */
    public Set<StationPermission> allChildren() {
        Set<StationPermission> known = allChildren;
        if (known != null) return known;

        Set<StationPermission> gathered = EnumSet.noneOf(StationPermission.class);
        for (StationPermission child : children) {
            gathered.add(child);
            gathered.addAll(child.allChildren());
        }
        known = Collections.unmodifiableSet(gathered);
        allChildren = known;
        return known;
    }

    /**
     * Checks whether this permission transitively includes the given permission.
     */
    public boolean includes(StationPermission permission) {
        return allChildren().contains(permission);
    }

    /**
     * Every permission that carries this one, itself included.
     *
     * <p>The counterpart of {@link #expand}: expansion answers what a holder may do, this answers
     * who holds it. Somebody granted only the station administrator right holds every management
     * right there is, and looking for the management right by name alone would not find them.
     *
     * @return the permission names that grant this permission
     */
    public List<String> grantedBy() {
        List<String> names = new ArrayList<>();
        for (StationPermission candidate : values()) {
            if (candidate == this || candidate.includes(this)) {
                names.add(candidate.name());
            }
        }
        return names;
    }
}
