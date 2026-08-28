/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.LoginNameService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The access a guardian manages for the members in their care: the address the account is reached
 * at, the name and password it signs in with, and whether it may sign in at all.
 *
 * <p>Everything here is deliberately narrow. A guardian speaks for a child, so they may give the
 * child an address, a name and switch its access on and off - but only for the members they manage,
 * only for the member types a guardian can be assigned to, and only for this one permission. Nothing
 * else about the account is theirs to change.
 *
 * <p>The name is what makes a child with no address of their own reachable at all: it is what they
 * type at the login screen, and the mail Ember would have written to them goes to their guardians.
 * That exception belongs to the two member types a guardian can be assigned to and to nobody else,
 * which is enforced by this service refusing every other type outright.
 */
@Singleton
public class ManagedAccessService {
    private static final Logger log = LoggerFactory.getLogger(ManagedAccessService.class);

    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final LoginNameService loginNameService;
    private final StationMemberService memberService;
    private final ManagedLoginNoticeService noticeService;
    private final AuthService authService;
    private final AccountEmailService accountEmailService;

    @Inject
    public ManagedAccessService(
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            LoginNameService loginNameService,
            StationMemberService memberService,
            ManagedLoginNoticeService noticeService,
            AuthService authService,
            AccountEmailService accountEmailService) {
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.loginNameService = loginNameService;
        this.memberService = memberService;
        this.noticeService = noticeService;
        this.authService = authService;
        this.accountEmailService = accountEmailService;
    }

    /**
     * What a guardian sees and may change about the access of a member in their care.
     *
     * @param email        the address the account carries, or null when it is only a synthetic one
     * @param username     the name the member signs in with, or null when there is none
     * @param loginEnabled whether the member may sign in
     * @param canSignIn    whether granting access is possible at all, which needs either an address
     *                     or a name to sign in with
     */
    public record ManagedAccess(String email, String username, boolean loginEnabled, boolean canSignIn) {}

    /**
     * Reads the access state of a managed member.
     *
     * @param guardianMemberId the member acting as guardian
     * @param memberId         the member in their care
     * @return the address, the name, and whether signing in is switched on
     */
    public ManagedAccess get(int guardianMemberId, int memberId) {
        StationMember member = requireManaged(guardianMemberId, memberId);
        var account = account(member);
        boolean real = account.hasRealEmail();
        return new ManagedAccess(
                real ? account.email() : null, account.username(), hasLogin(memberId), canSignIn(account));
    }

    /**
     * Sets or clears the name a managed member signs in with.
     *
     * <p>This is what makes a login possible for a child with no address of their own: the name is
     * what they type, and everything Ember would write to them goes to their guardians instead.
     * Clearing it is refused while it is the only way in and signing in is switched on, because that
     * would lock the member out without saying so.
     *
     * @param guardianMemberId the member acting as guardian
     * @param memberId         the member in their care
     * @param username         the new name, or null or blank to clear it
     * @return the access state after the change
     */
    public ManagedAccess setUsername(int guardianMemberId, int memberId, String username) {
        StationMember member = requireManaged(guardianMemberId, memberId);
        var account = account(member);
        accountRepository.updateUsername(account.id(), loginNameService.validatedFor(account, username));
        log.info(
                "Guardian {} set the username of managed member {} (account {})",
                guardianMemberId,
                memberId,
                account.id());
        return get(guardianMemberId, memberId);
    }

    /**
     * Sets the address a managed member's account is reached at.
     *
     * <p>The change takes effect at once rather than through the confirmation both addresses
     * normally exchange: the guardian is the one who reads the child's mail, and a member without a
     * real address has nothing to confirm from. Open sessions of that member end, and where there
     * was a real address before, both of them are told.
     *
     * @param guardianMemberId the member acting as guardian
     * @param memberId         the member in their care
     * @param email            the new address
     * @return the access state after the change
     */
    public ManagedAccess setEmail(int guardianMemberId, int memberId, String email) {
        StationMember member = requireManaged(guardianMemberId, memberId);
        var account = account(member);
        if (accountEmailService.setEmail(account.id(), email)) {
            log.info(
                    "Guardian {} set the email of managed member {} (account {})",
                    guardianMemberId,
                    memberId,
                    account.id());
        }
        return get(guardianMemberId, memberId);
    }

    /**
     * Sets the password a managed member signs in with.
     *
     * <p>Only for a member with no address of their own, whose invitation lands in the guardian's
     * postbox anyway, so this spares them the detour rather than granting them anything new. A
     * member with an address of their own keeps that door to themselves: setting it here would be
     * taking over an account past its owner's postbox.
     *
     * @param guardianMemberId the member acting as guardian
     * @param memberId         the member in their care
     * @param password         the new password
     * @return the access state after the change
     */
    public ManagedAccess setPassword(int guardianMemberId, int memberId, String password) {
        StationMember member = requireManaged(guardianMemberId, memberId);
        var account = account(member);
        if (account.hasRealEmail()) {
            throw new ForbiddenResponse("This member has an address of their own and sets their own password");
        }
        if (password == null || password.isBlank()) {
            throw new BadRequestResponse("setPassword.passwordTooShort");
        }
        switch (authService.setPasswordFor(account, password)) {
            case PASSWORD_TOO_SHORT -> throw new BadRequestResponse("setPassword.passwordTooShort");
            case PASSWORD_BREACHED -> throw new BadRequestResponse("setPassword.passwordBreached");
            default ->
                log.info(
                        "Guardian {} set the password of managed member {} (account {})",
                        guardianMemberId,
                        memberId,
                        account.id());
        }
        return get(guardianMemberId, memberId);
    }

    /**
     * Switches signing in on or off for a managed member.
     *
     * <p>Switching it on needs an address to send the invitation to; switching it off ends the
     * sessions that are open, so the change is not merely cosmetic.
     *
     * <p>The member is told by mail, but not straight away: the change is handed to
     * {@link ManagedLoginNoticeService}, which waits a few minutes so a toggle flicked by mistake
     * and flicked back reaches nobody. That is also where the password-setup mail for an account
     * nobody has claimed yet comes from.
     *
     * @param guardianMemberId the member acting as guardian
     * @param memberId         the member in their care
     * @param enabled          whether the member may sign in
     * @return the access state after the change
     */
    public ManagedAccess setLogin(int guardianMemberId, int memberId, boolean enabled) {
        StationMember member = requireManaged(guardianMemberId, memberId);
        var account = account(member);
        var permission = memberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow(() -> new BadRequestResponse("The login permission does not exist"));

        if (enabled) {
            if (!canSignIn(account)) {
                throw new BadRequestResponse(
                        "Set an email address or a username before allowing this member to sign in");
            }
            if (!hasLogin(memberId)) {
                memberRepository.grantPermission(memberId, permission.id());
                noticeService.record(memberId, true);
            }
        } else if (hasLogin(memberId)) {
            memberRepository.revokePermission(memberId, permission.id());
            accountRepository.deleteSessionsByAccount(account.id());
            noticeService.record(memberId, false);
        }
        log.info("Guardian {} set login of managed member {} to {}", guardianMemberId, memberId, enabled);
        return get(guardianMemberId, memberId);
    }

    private boolean hasLogin(int memberId) {
        return memberRepository.hasPermission(memberId, StationPermission.LOGIN);
    }

    /** There has to be something to type at the login screen: an address, or a name of their own. */
    private static boolean canSignIn(Account account) {
        return account.hasRealEmail() || account.username() != null;
    }

    private Account account(StationMember member) {
        if (member.accountId() == null) {
            throw new BadRequestResponse("This member has no account");
        }
        return accountRepository.findById(member.accountId()).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Refuses anything but a member the guardian actually manages, and anything but the member
     * types a guardian can be assigned to in the first place.
     */
    private StationMember requireManaged(int guardianMemberId, int memberId) {
        boolean manages =
                memberService.findManaged(guardianMemberId).stream().anyMatch(managed -> managed.id() == memberId);
        if (!manages) {
            throw new ForbiddenResponse("You do not manage this member");
        }
        StationMember member = memberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        if (member.userType() != StationUserType.MEMBER && member.userType() != StationUserType.TRIAL) {
            throw new ForbiddenResponse("Only members and trial members are managed this way");
        }
        return member;
    }
}
