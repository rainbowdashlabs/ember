package dev.chojo.ember.api.roles;

import io.javalin.security.RouteRole;

/**
 * Defines the user type on the instance level.
 * A user can only have one type at a time.
 * The user type itself should not grant any access at all. This is only defined by the granted default permissions.
 * The instance user type should not affect permissions on the station level.
 */
public enum InstanceUserType {
    /**
     * A normal user on the instance. Only has access to the assigned stations
     */
    USER,

    /**
     * An administrator of the instance. Has access to all stations
     */
    ADMINISTRATOR(InstancePermission.ADMINISTRATOR);

    private final InstancePermission[] defaultPermission;

    InstanceUserType() {
        this.defaultPermission = new InstancePermission[0];
    }
    InstanceUserType(InstancePermission... defaultPermission) {
        this.defaultPermission = defaultPermission;
    }
}
