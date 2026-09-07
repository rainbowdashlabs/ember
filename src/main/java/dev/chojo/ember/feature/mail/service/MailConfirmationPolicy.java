/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Demo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Whether a confirmation asked for by mail is worth asking for on this instance.
 *
 * <p>Several things wait for a link before they happen: an address is verified, an address is
 * changed, a station is deleted. On an instance with no way of sending, that link is never written
 * and never arrives, so the wait is not a safeguard but a dead end: the thing simply never happens
 * and nothing says why. Where nothing can be sent, the confirmation counts as given and the thing
 * happens at once.
 *
 * <p>This holds for every such confirmation and not only for those that prove an address. One that
 * asks whether somebody really meant it, deleting a station being the example, is no different: it
 * is a question nobody can be asked, and leaving it standing would leave the station undeletable
 * rather than safe. Whoever installs an instance without mail has said what they want.
 *
 * <p>What it is not is a way out of proving a password. A setup or reset link hands over a
 * credential rather than confirming something already chosen, and there is nothing to grant early:
 * granting one would mean handing anybody a password they never set. Those links are untouched.
 *
 * <p>A public demo counts as an instance that can send, although it delivers nothing. Its mail is
 * swallowed on purpose, so that the flows can be walked through and looked at, and granting every
 * confirmation there would take the two steps out of the one place they are ever demonstrated, and
 * would let a passer-by delete a demo station with a single click. What is missing on a demo is
 * delivery; what this asks about is whether a provider was ever set up.
 */
@Singleton
public class MailConfirmationPolicy {

    private final MailChainService chainService;
    private final Demo demo;

    @Inject
    public MailConfirmationPolicy(MailChainService chainService, Demo demo) {
        this.chainService = chainService;
        this.demo = demo;
    }

    /**
     * Whether a confirmation asked for by mail counts as given without anybody clicking it.
     */
    public boolean confirmationCountsAsGranted() {
        return !demo.enabled() && chainService.forInstance().isEmpty();
    }
}
