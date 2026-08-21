/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.feature.inventory.entity.InventoryType;
import io.javalin.security.RouteRole;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A permission represents the permission to perform an action or a set of actions.
 * A permission might contain other permissions, which are implicitly granted.
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
    ATTENDANCE_READ,

    /**
     * Allows creating a new attendance session, editing and register attendance
     */
    ATTENDANCE_EDIT(ATTENDANCE_READ),

    /**
     * Allows configuring the attendance templates
     */
    ATTENDANCE_CONFIGURE,

    /**
     * Allows exporting attendance data
     */
    ATTENDANCE_EXPORT(ATTENDANCE_READ),

    /**
     * Grants access to all attendance actions.
     */
    ATTENDANCE_MANAGER(ATTENDANCE_EDIT, ATTENDANCE_CONFIGURE, ATTENDANCE_EXPORT, ATTENDANCE_READ),

    /**
     * Allows creating new inventory items of type {@link InventoryType#EXTERNAL}
     */
    INVENTORY_CREATE_EXTERNAL,

    /**
     * Allows creating new inventory items of type {@link InventoryType#INTERNAL}
     */
    INVENTORY_CREATE_INTERNAL,

    /**
     * Allows reading all inventory data of users
     */
    INVENTORY_READ,

    /**
     * Allows creating new inventory items
     */
    INVENTORY_CREATE(INVENTORY_CREATE_EXTERNAL, INVENTORY_CREATE_INTERNAL, INVENTORY_READ),

    /**
     * Allows managing the inventory exchanges.
     */
    INVENTORY_EXCHANGE(INVENTORY_CREATE_EXTERNAL, INVENTORY_READ),

    /**
     * Allows managing inventory procurements.
     * Only allows creating. To mark as done the {@link #ATTENDANCE_EDIT} permission is required for the matching inventory type
     */
    INVENTORY_PROCUREMENT,

    /**
     * Allows performing an inventory check on a user.
     */
    INVENTORY_CHECK(INVENTORY_CREATE_EXTERNAL),

    /**
     * Allows creating new inventory lending requests and managing outgoing requests.
     */
    INVENTORY_LENDING_REQUEST,

    /**
     * Allows managing inventory lending requests
     */
    INVENTORY_LENDING_MANAGER(INVENTORY_LENDING_REQUEST),

    /**
     * Allows editing all inventory data of users
     */
    INVENTORY_EDIT(INVENTORY_CREATE),

    /**
     * Allows assigning items to members and accepting returns. Off by default
     * for every role; the station owner grants it explicitly.
     */
    INVENTORY_ASSIGN(INVENTORY_READ),

    /**
     * Allows creating, renaming, moving and deleting storage containers, and
     * placing items into containers.
     */
    INVENTORY_STORAGE(INVENTORY_READ),

    /**
     * Allows managing inventory items and lending requests.
     */
    INVENTORY_MANAGER(
            INVENTORY_ASSIGN,
            INVENTORY_CHECK,
            INVENTORY_CREATE,
            INVENTORY_EDIT,
            INVENTORY_EXCHANGE,
            INVENTORY_LENDING_MANAGER,
            INVENTORY_PROCUREMENT,
            INVENTORY_READ,
            INVENTORY_STORAGE),

    /**
     * Allows managing event templates
     */
    EVENT_MANAGE_TEMPLATE,

    /**
     * Allows managing event categories
     */
    EVENT_MANAGE_CATEGORY,

    /**
     * Allows creating and editing events
     */
    EVENT_EDIT,

    /**
     * Allows confirming and deny registrations.
     */
    EVENT_REGISTRATION,

    /**
     * Allows federating events to other stations
     */
    EVENTS_FEDERATE(EVENT_EDIT),

    /**
     * Allows managing events
     */
    EVENT_MANAGER(EVENTS_FEDERATE, EVENT_REGISTRATION, EVENT_MANAGE_TEMPLATE, EVENT_MANAGE_CATEGORY),

    /**
     * Allows reading all member data.
     */
    MEMBER_READ,

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
    MEMBER_EDIT(MEMBER_READ, MEMBER_SELF_UPLOAD),

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
    WAITLIST_READ,

    /**
     * Allows adding entries to the member waitlist.
     */
    WAITLIST_ADD(WAITLIST_READ),

    /**
     * Allows editing and managing entries on the member waitlist.
     */
    WAITLIST_EDIT(WAITLIST_ADD),

    /**
     * Allows managing the member waitlist.
     */
    WAITLIST_MANAGER(WAITLIST_EDIT),

    /**
     * Allows creating news entries
     */
    NEWS_EDIT,

    /**
     * Allows federating news entries to other stations
     */
    NEWS_FEDERATE,

    /**
     * Allows managing news entries
     */
    NEWS_MANAGER(NEWS_EDIT, NEWS_FEDERATE),

    /**
     * Allows reading poll results
     */
    POLL_VIEW_RESULTS,

    /**
     * Allows creating polls and forms
     */
    POLL_CREATE(POLL_VIEW_RESULTS),

    /**
     * Allows managing polls and forms
     */
    POLL_MANAGER(POLL_CREATE),

    /**
     * Allows creating lost and found items
     */
    LOST_AND_FOUND_CREATE,

    /**
     * Allows managing lost and found items
     */
    LOST_AND_FOUND_MANAGE(LOST_AND_FOUND_CREATE),

    /**
     * Allows managing lost and found items
     */
    LOST_AND_FOUND_MANAGER(LOST_AND_FOUND_MANAGE),

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
    TEST_CATALOG_VIEW,
    /**
     * Allows editing quizzes
     */
    TEST_CATALOG_EDIT(TEST_CATALOG_VIEW),
    /**
     * Allows configuring new quizzes
     */
    TEST_CONFIGURE(TEST_CATALOG_EDIT),

    /**
     * Allows reading the test results of users
     */
    TEST_RESULT_READ,

    /**
     * Allows reviewing quizzed/grading them
     */
    TEST_REVIEW(TEST_RESULT_READ),

    /**
     * Allows managing tests
     */
    TEST_MANAGER(TEST_CONFIGURE, TEST_REVIEW),

    /**
     * Allows filling out a protocol
     */
    PROTOCOL_TESTER,
    /**
     * Allows creating a new protocol run
     */
    PROTOCOL_CREATE(PROTOCOL_TESTER),
    /**
     * Allows configuring protocols
     */
    PROTOCOL_CONFIGURE(PROTOCOL_CREATE),

    PROTOCOL_MANAGER(PROTOCOL_CONFIGURE),

    /**
     * Allows generally using boards.
     */
    BOARD_USE,

    /**
     * Allows editing and creating boards
     */
    BOARD_EDIT(BOARD_USE),

    /**
     * Allows editing and creating boards
     */
    BOARD_FEDERATE(BOARD_EDIT),

    BOARD_MANAGER(BOARD_FEDERATE),

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
    PROCEDURE_READ,

    /**
     * Allows creating, assigning, and progressing procedures.
     */
    PROCEDURE_EDIT(PROCEDURE_READ),

    /**
     * Allows creating and managing procedure templates.
     */
    PROCEDURE_MANAGER(PROCEDURE_EDIT),

    /**
     * Knowledgebase management
     */
    KNOWLEDGE_EDIT,

    /**
     * Allows changing federation settings of the knowledgebase.
     */
    KNOWLEDGE_FEDERATE(KNOWLEDGE_EDIT),

    /**
     * Knowledgebase management
     */
    KNOWLEDGE_MANAGER(KNOWLEDGE_EDIT, KNOWLEDGE_FEDERATE),

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

    private final StationPermission[] children;
    private volatile Set<StationPermission> allChildren;

    StationPermission(StationPermission... children) {
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
}
