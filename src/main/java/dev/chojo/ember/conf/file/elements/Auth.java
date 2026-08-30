/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * Authentication configuration controlling token sizes and expiration durations
 * for sessions, email verification, and password reset tokens.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class Auth {
    /** The longest a setup link may be made to live, whatever the configuration asks for. */
    public static final int SETUP_TOKEN_MAX_DAYS = 30;

    @Overwrite(env = @Env)
    private int tokenBytes = 32;

    @Overwrite(env = @Env)
    private int verifyTokenHours = 24;

    @Overwrite(env = @Env)
    private int passwordTokenHours = 72;

    /**
     * Lifetime of a self-service password-reset link. Kept short because it is a
     * bearer secret delivered by email; the longer {@link #passwordTokenHours}
     * window applies only to operator-initiated invites and admin resets.
     */
    @Overwrite(env = @Env)
    private int resetTokenHours = 1;

    /**
     * How many days the link that sets up a new account stays good for.
     *
     * <p>Its own setting, because it is not a reset. Somebody who forgot their password is at the
     * screen now and wants back in within the hour; somebody handed an account is invited, and the
     * invitation waits for a holiday, a term break, or the evening they next look at their mail. A
     * link that dies over a long weekend leaves an operator sending invitations twice.
     *
     * <p>Capped at {@link #SETUP_TOKEN_MAX_DAYS} days however it is configured. It is still a bearer
     * secret in a mailbox, and a link that never dies is a password that was never chosen.
     */
    @Overwrite(env = @Env)
    private int setupTokenDays = 30;

    /**
     * How long a mail waits after a guardian switched signing in on or off for a member in their
     * care, before it is sent.
     *
     * <p>The switch itself takes effect at once; only the telling waits. A guardian who flicks it by
     * accident and flicks it straight back inside this window sends the member nothing at all, which
     * is the whole point of the delay. Zero sends with the next sweep, about a minute later.
     */
    @Overwrite(env = @Env)
    private int managedLoginNoticeMinutes = 5;

    /**
     * How long a session lasts on a device the person signing in vouched for.
     *
     * <p>This is the long duration, the one somebody asks for by ticking the box on their own
     * machine, and thirty days is what that box is generally taken to mean. It has to be at least
     * {@link #untrustedSessionMinutes}: a machine nobody vouched for keeping its session longer
     * than one somebody did turns the box into a penalty, which is what the default used to do.
     */
    @Overwrite(env = @Env)
    private int sessionMinutes = 43200;

    /**
     * How long a session lasts on a device the person signing in did not vouch for.
     *
     * <p>Kept short on purpose. The long duration is what somebody asks for by ticking the box on
     * their own machine; a borrowed or shared one gets an hour whatever the instance allows.
     */
    @Overwrite(env = @Env)
    private int untrustedSessionMinutes = 60;

    /**
     * Server-side secret mixed into the HMAC used to hash session and recovery
     * tokens before they are stored in the database. Empty by default; the
     * application refuses to boot in production with a blank value. A
     * deterministic placeholder is substituted during demo / dev runs so local
     * smoke tests do not have to configure a pepper.
     */
    @Overwrite(env = @Env)
    private String tokenPepper = "";

    /**
     * "Have I Been Pwned" k-anonymity breach-check configuration. New passwords
     * are checked synchronously at set time and asynchronously after every
     * successful login; pwned credentials are rejected on set and flagged for
     * forced rotation on login.
     */
    private HibpSettings hibp = new HibpSettings();

    private TwoFactorSettings twoFactor = new TwoFactorSettings();

    public int tokenBytes() {
        return tokenBytes;
    }

    public int verifyTokenHours() {
        return verifyTokenHours;
    }

    public int passwordTokenHours() {
        return passwordTokenHours;
    }

    /**
     * The setup-link lifetime actually applied, which is what was configured or the ceiling,
     * whichever is shorter. At least a day, so a nonsensical zero does not hand out dead links.
     */
    public int setupTokenDays() {
        return Math.clamp(setupTokenDays, 1, SETUP_TOKEN_MAX_DAYS);
    }

    public int resetTokenHours() {
        return resetTokenHours;
    }

    public int sessionMinutes() {
        return Math.max(5, sessionMinutes);
    }

    /**
     * How long the mail about a guardian's access change waits, never less than no wait at all.
     */
    public int managedLoginNoticeMinutes() {
        return Math.max(0, managedLoginNoticeMinutes);
    }

    /**
     * How long a session lasts on a machine nobody vouched for, never longer than one somebody did.
     *
     * <p>The settings screen refuses to save the two the wrong way round, but a config file written
     * by hand can still say it, and the ordering has to hold whichever way the value arrived.
     */
    public int untrustedSessionMinutes() {
        return Math.min(Math.max(5, untrustedSessionMinutes), sessionMinutes());
    }

    /**
     * How long a session lasts on this kind of device.
     *
     * @param trustedDevice whether the person signing in vouched for the machine
     */
    public int sessionMinutes(boolean trustedDevice) {
        return trustedDevice ? sessionMinutes() : untrustedSessionMinutes();
    }

    /**
     * Returns the server-side pepper mixed into HMAC-SHA-256 when hashing
     * bearer-shaped tokens for at-rest storage.
     */
    public String tokenPepper() {
        return tokenPepper;
    }

    public HibpSettings hibp() {
        return hibp;
    }

    public TwoFactorSettings twoFactor() {
        return twoFactor;
    }

    @Override
    public String toString() {
        return "Auth{" + "tokenBytes="
                + tokenBytes + ", verifyTokenHours="
                + verifyTokenHours + ", passwordTokenHours="
                + passwordTokenHours + ", resetTokenHours="
                + resetTokenHours + ", sessionMinutes="
                + sessionMinutes + ", tokenPepperConfigured="
                + !tokenPepper.isBlank() + ", hibp="
                + hibp + '}';
    }
}
