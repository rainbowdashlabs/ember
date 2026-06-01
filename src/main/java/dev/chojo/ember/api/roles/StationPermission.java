package dev.chojo.ember.api.roles;

import dev.chojo.ember.feature.inventory.entity.InventoryType;
import io.javalin.security.RouteRole;

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
     * Allows creating a new attendance session and register attendance
     */
    ATTENDANCE_CREATE,

    /**
     * Allows configuring the attendance templates
     */
    ATTENDANCE_CONFIGURE,

    /**
     * Allows exporting attendance data
     */
    ATTENDANCE_EXPORT,

    /**
     * Grants access to all attendance actions.
     */
    ATTENDANCE_MANAGER(ATTENDANCE_CREATE, ATTENDANCE_CONFIGURE, ATTENDANCE_EXPORT),

    /**
     * Allows creating new inventory items of type {@link InventoryType#EXTERNAL}
     */
    INVENTORY_CREATE_EXTERNAL,

    /**
     * Allows creating new inventory items of type {@link InventoryType#INTERNAL}
     */
    INVENTORY_CREATE_INTERNAL,

    /**
     * Allows creating new inventory items
     */
    INVENTORY_CREATE(INVENTORY_CREATE_EXTERNAL, INVENTORY_CREATE_INTERNAL),

    /**
     * Allows reading all inventory data of users
     */
    INVENTORY_READ,

    /**
     * Allows managing inventory procurements.
     * Only allows creating. To mark as done the {@link #ATTENDANCE_CREATE} permission is required for the matching inventory type
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
    INVENTORY_LENDING_MANAGER,

    /**
     * Allows managing inventory items and lending requests.
     */
    INVENTORY_MANAGER(INVENTORY_CREATE, INVENTORY_READ, INVENTORY_CHECK, INVENTORY_LENDING_REQUEST, INVENTORY_LENDING_MANAGER, INVENTORY_PROCUREMENT),

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
    EVENT_CONFIGURE,

    /**
     * Allows confirming and deny registrations.
     */
    EVENT_REGISTRATION,

    /**
     * Allows federating events to other stations
     */
    EVENTS_FEDERATE(EVENT_CONFIGURE),

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
     * Allows editing and create members.
     */
    MEMBER_EDIT(MEMBER_READ),

    /**
     * Allows configuring the member fields config.
     */
    MEMBER_FIELDS,

    /**
     * Allows managing members
     */
    MEMBER_MANAGER(MEMBER_EDIT, MEMBER_NOTES, MEMBER_CHANGES, MEMBER_MANAGE_GROUP, MEMBER_MANAGE_TAGS, MEMBER_EDIT, MEMBER_FIELDS),

    /**
     * Allows reading the member waitlist.
     */
    WAITLIST_READ,

    /**
     * Allows adding member to the member waitlist.
     */
    WAITLIST_ADD(WAITLIST_READ),

    /**
     * Allows managing the member waitlist.
     */
    WAITLIST_MANAGER(WAITLIST_READ, MEMBER_EDIT),

    /**
     * Allows creating news entries
     */
    NEWS_CREATE,

    /**
     * Allows federating news entries to other stations
     */
    NEWS_FEDERATE,

    /**
     * Allows managing news entries
     */
    NEWS_MANAGER(NEWS_CREATE, NEWS_FEDERATE),

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
     * Allows managing tests
     */
    TEST_MANAGER(TEST_CONFIGURE),

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

    QUIZ_MANAGER(PROTOCOL_MANAGER, TEST_MANAGER),

    /**
     * Allows editing and creating boards
     */
    BOARD_EDIT,

    BOARD_MANAGER(BOARD_EDIT),

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
    STATION_MANAGER(STATION_LOOK_AND_FEEL, STATION_GENERAL, STATION_MAIL, STATION_MODULES, STATION_IMPORT_EXPORT, STATION_STATISTICS, STATION_FEDERATION),

    /**
     * The station administrator permission. Grants access to all station management features.
     */
    STATION_ADMINISTRATOR(STATION_MANAGER,KNOWLEDGE_MANAGER, BOARD_MANAGER, QUIZ_MANAGER, LOST_AND_FOUND_MANAGER, POLL_MANAGER, NEWS_MANAGER, NEWS_FEDERATE, WAITLIST_MANAGER, MEMBER_MANAGER, EVENT_MANAGER, INVENTORY_MANAGER, ATTENDANCE_MANAGER);

    private final StationPermission[] children;
    private Set<StationPermission> allChildren;

    public StationPermission[] getChildren() {
        return children;
    }

    StationPermission(StationPermission... children) {
        this.children = children;
    }

    /**
     * Expands a set of roles to include all transitively contained child roles.
     */
    public static Set<StationPermission> expand(Set<StationPermission> roles) {
        Set<StationPermission> expanded = EnumSet.copyOf(roles);
        for (StationPermission role : roles) {
            expanded.addAll(role.allChildren());
        }
        return expanded;
    }

    /**
     * Returns all roles transitively included by this role (direct and indirect children).
     */
    public Set<StationPermission> allChildren() {
        if (allChildren == null) {
            allChildren = EnumSet.noneOf(StationPermission.class);
            for (StationPermission child : children) {
                allChildren.add(child);
                allChildren.addAll(child.allChildren());
            }
        }
        return allChildren;
    }

    /**
     * Checks whether this role transitively includes the given role.
     */
    public boolean includes(StationPermission role) {
        return allChildren().contains(role);
    }
}
