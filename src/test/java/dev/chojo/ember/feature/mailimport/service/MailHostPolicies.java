/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;

/**
 * The two host policies the mail import stories need.
 *
 * <p>A test mail server answers on loopback, which a deployment that has not allowed private hosts would
 * refuse outright, so most of these stories run against a policy that has. The strict one is what proves
 * the refusal itself.
 */
final class MailHostPolicies {

    private MailHostPolicies() {}

    /** What a deployment with {@code allowPrivateHosts} on does, which is where a test server lives. */
    static MailHostPolicy allowingTheLocalNetwork() {
        return new MailHostPolicy(new RemoteUrlValidator(
                new Federation() {
                    @Override
                    public boolean allowPrivateHosts() {
                        return true;
                    }
                },
                new Demo()));
    }

    /** What an ordinary deployment does, where nothing inside the network may be reached at all. */
    static MailHostPolicy publicHostsOnly() {
        return new MailHostPolicy(new RemoteUrlValidator(new Federation(), new Demo()));
    }
}
