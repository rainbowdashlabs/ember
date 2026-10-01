/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.LoginNameService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * The accounts behind the members of a station, as whoever manages the members acts on them:
 * correcting a name or an address, and the checks that decide whose account may be acted on at
 * all.
 */
@Singleton
public class MemberAccountService {
    private final AccountRepository accountRepository;
    private final StationMemberRepository memberRepository;
    private final AuthService authService;
    private final LoginNameService loginNameService;
    private final AccountEmailService accountEmailService;
    private final StepUpGuard stepUpGuard;
    private final MemberNameResolver nameResolver;

    @Inject
    public MemberAccountService(
            AccountRepository accountRepository,
            StationMemberRepository memberRepository,
            AuthService authService,
            LoginNameService loginNameService,
            AccountEmailService accountEmailService,
            StepUpGuard stepUpGuard,
            MemberNameResolver nameResolver) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.authService = authService;
        this.loginNameService = loginNameService;
        this.accountEmailService = accountEmailService;
        this.stepUpGuard = stepUpGuard;
        this.nameResolver = nameResolver;
    }

    /**
     * Refuses an account that is no member of the caller's station, answering as though it were
     * not there, so an account of another station cannot be acted on or probed through these
     * routes.
     *
     * @param accountId the account
     * @param stationId the caller's station, or null when none is chosen
     */
    public void requireStationAccount(int accountId, @Nullable Integer stationId) {
        if (stationId == null
                || memberRepository
                        .findByStationAndAccount(stationId, accountId)
                        .isEmpty()) {
            throw Refusal.MEMBER_NOT_HERE.raise();
        }
    }

    /**
     * The account of a member of the caller's station that the caller may act on.
     *
     * @param accountId the account
     * @param session   who is asking
     * @param missing   the refusal for an account that is gone
     * @return the account
     */
    public Account actionableAccount(int accountId, StationSession session, Refusal missing) {
        requireStationAccount(accountId, session.stationId());
        Account target = accountRepository.findById(accountId).orElseThrow(missing::raise);
        requireNotAboveActor(target, session.user());
        return target;
    }

    /**
     * The account a member manager may hand a passkey code to: one with no address of its own.
     * Somebody with an address is refused, since the mail path is right there and is the one
     * with a second party in it.
     *
     * @param accountId the account
     * @param session   who is asking
     * @return the account
     */
    public Account addresslessAccount(int accountId, StationSession session) {
        Account target = actionableAccount(accountId, session, Refusal.ACCOUNT_NOT_HERE_ON_PASSKEY_CODE);
        if (target.hasRealEmail()) {
            throw Refusal.MEMBER_HAS_OWN_ADDRESS.raise();
        }
        return target;
    }

    /**
     * Refuses acting on an account that administers the instance from a permission below it.
     * Resetting an administrator's password ends every session and token they hold, and moving
     * their address aims every later mail at whoever chose it; the two together are a takeover
     * kit, so neither is reachable from a mere member-editing permission.
     */
    private static void requireNotAboveActor(Account target, UserSession actor) {
        if (target.instanceUserType() == InstanceUserType.ADMINISTRATOR
                && actor.instanceUserType() != InstanceUserType.ADMINISTRATOR) {
            throw Refusal.ACCOUNT_ABOVE_YOU.raise();
        }
    }

    /**
     * Changes an account's name, sign-in name and address.
     *
     * <p>Everybody may change their own. Whoever administers the instance reaches any account,
     * without being at the same station: the account this exists for is another administrator,
     * whose address cannot be corrected by the confirmation that would be sent to it. Anybody else
     * needs the right to edit members and the account has to belong to a member of their station.
     *
     * <p>The name is written with the address the account already has, so that the two ways of
     * changing an address are the only things that ever move it. Somebody putting their own
     * address right confirms it from both ends, which is what stops a stolen session walking off
     * with the account. Moving somebody else's address aims every later mail at whoever chose it,
     * so it takes a fresh proof, never reaches upwards, and is committed at once.
     *
     * @param session   who is asking
     * @param stationId the station they ask from, or null when none is chosen
     * @param accountId the account
     * @param request   the new values
     * @return what became of the address
     */
    public UpdateAccountResponse update(
            UserSession session, @Nullable Integer stationId, int accountId, UpdateAccountRequest request) {
        boolean actsForSomebodyElse = session.accountId() != accountId;
        if (actsForSomebodyElse && !session.hasInstancePermission(InstancePermission.ADMINISTRATOR)) {
            if (!session.hasPermission(StationPermission.MEMBER_EDIT)) {
                throw Refusal.ACCOUNT_NOT_YOURS_TO_CHANGE.raise();
            }
            requireStationAccount(accountId, stationId);
        }
        var existing = accountRepository.findById(accountId).orElseThrow(Refusal.ACCOUNT_NOT_HERE_ON_CHANGE::raise);
        if (!accountRepository.update(accountId, existing.email(), request.firstName(), request.lastName())) {
            throw Refusal.MEMBER_NOT_HERE_ON_CHANGE.raise();
        }
        nameResolver.forgetAccount(accountId);
        if (request.username() != null) {
            accountRepository.updateUsername(accountId, loginNameService.validatedFor(existing, request.username()));
        }
        if (!changesAddress(existing, request.email())) {
            return new UpdateAccountResponse("Account updated", null);
        }
        if (actsForSomebodyElse) {
            requireNotAboveActor(existing, session);
            stepUpGuard.require(session, StepUpCategory.ACCOUNT_SECURITY);
            accountEmailService.setEmailFor(session.accountId(), accountId, request.email());
            return new UpdateAccountResponse("Account updated", AuthService.EmailChangeResult.COMMITTED);
        }
        var outcome = authService.requestEmailChange(accountId, request.email());
        if (outcome == AuthService.EmailChangeResult.DUPLICATE) {
            throw Refusal.ACCOUNT_ADDRESS_TAKEN.raise();
        }
        return new UpdateAccountResponse("Account updated", outcome);
    }

    private static boolean changesAddress(Account existing, String email) {
        return email != null && !email.isBlank() && !email.equalsIgnoreCase(existing.email());
    }

    /**
     * @param username the name this account signs in with beside its address. Absent leaves the name
     *                 as it is; empty clears it.
     */
    public record UpdateAccountRequest(String email, String username, String firstName, String lastName) {}

    /**
     * The answer to an account update.
     *
     * @param emailChange what became of an address given in the same call: {@code null} when the
     *                    address was left alone, COMMITTED when it is already the account's, and
     *                    WAITING when it becomes so once a link in the reader's mail is clicked
     */
    public record UpdateAccountResponse(String message, AuthService.@Nullable EmailChangeResult emailChange) {}
}
