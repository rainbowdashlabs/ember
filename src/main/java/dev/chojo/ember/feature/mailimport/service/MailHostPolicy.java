/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Optional;

/**
 * Which hosts a station may point a mailbox at, and how it may talk to them.
 *
 * <p>Connecting a mailbox is a station setting rather than an operator one, so without this a station
 * manager could aim it at loopback or an address inside the deployment and read back from the connection
 * test whether anything answered there. The same check the storage backends use answers that, and it is
 * asked twice: once as the mailbox is written, so somebody setting one up is told straight away, and
 * again as the connection is opened, which covers a row written before this existed and a name that
 * resolves somewhere else the second time.
 *
 * <p><b>Unencrypted is only ever offered on your own network.</b> A password sent in the clear to a host
 * on the internet is a password given away, so {@link MailSecurity#NONE} needs an address only the local
 * network can reach. The two rules compose rather than fight: a private address needs the operator to
 * have allowed private hosts at all, and clear text needs a private address, so clear text to a public
 * host stops being expressible either way.
 */
@Singleton
public class MailHostPolicy {

    private final RemoteUrlValidator urlValidator;

    @Inject
    public MailHostPolicy(RemoteUrlValidator urlValidator) {
        this.urlValidator = urlValidator;
    }

    /**
     * What is wrong with reaching this host this way, or nothing where nothing is.
     *
     * <p>A reason rather than an exception, because the two callers owe their answer to different people:
     * a page gets a refusal as it types, and a cycle running in the background gets a failure written onto
     * the mailbox.
     *
     * @param host     the host as the mailbox holds it
     * @param security how the connection would be secured
     * @return why it is refused, or empty where it is not
     */
    public Optional<String> objection(String host, MailSecurity security) {
        if (host == null || host.isBlank()) return Optional.of("A mailbox needs a host");
        if (!urlValidator.isHostAllowed(host)) {
            return Optional.of("%s is not an address this instance may connect to".formatted(host.trim()));
        }
        if (security == MailSecurity.NONE && !onTheLocalNetwork(host)) {
            return Optional.of("An unencrypted connection is only offered to a host on your own network");
        }
        return Optional.empty();
    }

    /**
     * Whether every address the name resolves to is one only the local network can reach.
     *
     * <p>Every one of them rather than any: a name answering with a private address and a public one is a
     * name that can be connected to over the internet, and the clear text password would go there.
     */
    private static boolean onTheLocalNetwork(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host.trim());
        } catch (Exception e) {
            return false;
        }
        if (addresses.length == 0) return false;
        for (InetAddress address : addresses) {
            if (!isPrivate(address)) return false;
        }
        return true;
    }

    private static boolean isPrivate(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || isUniqueLocal(address);
    }

    /** The IPv6 half of a private range, {@code fc00::/7}, which the address class does not answer for. */
    private static boolean isUniqueLocal(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
