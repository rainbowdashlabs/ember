/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequest;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Who may answer a code another device is showing, and whose name the answer is given in.
 *
 * <p>A request is answered by the account it concerns or by somebody in that account's care: a
 * member signed in by their guardian has no password and no passkey, so the guardian is the only one
 * who can answer a demand made of them. Everybody else is told nothing, which is exactly what a
 * wrong code already earns, so the answer cannot be read as "this code exists".
 */
@Singleton
public class DeviceApprovalGuards {
    private final AccountRepository accounts;

    @Inject
    public DeviceApprovalGuards(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Whether this account may see the request at all, let alone answer it.
     *
     * <p>A sign-in and an enrolment are read against the account the requesting device named, a
     * step-up against the one whose session raised it. A request whose identifier matched no account
     * names nobody and so passes for nobody, which is how an address that does not exist here comes
     * to be answered like one that does.
     *
     * @param accountId the account reading the request
     */
    public boolean mayConfirm(int accountId, DeviceRequest open) {
        Integer subject = open.is(DeviceRequestPurpose.STEP_UP) ? open.requestingAccountId() : open.namedAccountId();
        if (subject == null) return false;
        return subject == accountId || accounts.isGuardianOf(accountId, subject);
    }

    /**
     * Whose step-up this is, where it is not the reader's own.
     *
     * <p>A guardian may be answering for themselves or for a child, and the two look identical
     * otherwise. Naming them is what lets a guardian refuse a code that is not the one somebody beside
     * them just asked for. Their own request says nothing, because there is nothing to tell apart.
     *
     * @param accountId the account reading the request
     * @return the name of whoever raised the step-up, or null
     */
    public @Nullable String stepUpSubject(int accountId, DeviceRequest open) {
        if (!open.is(DeviceRequestPurpose.STEP_UP)) return null;
        Integer requester = open.requestingAccountId();
        if (requester == null || requester == accountId) return null;
        return accounts.findById(requester).map(Account::fullName).orElse(null);
    }

    /**
     * What an account is called on the approval screen, empty where it is gone.
     */
    public String nameOf(int accountId) {
        return accounts.findById(accountId).map(Account::fullName).orElse("");
    }
}
