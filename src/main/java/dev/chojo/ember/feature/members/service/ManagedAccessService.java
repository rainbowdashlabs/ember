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
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The access a guardian manages for the members in their care: the address the account is reached
 * at, and whether that account may sign in at all.
 *
 * <p>Everything here is deliberately narrow. A guardian speaks for a child, so they may give the
 * child an address and switch its access on and off - but only for the members they manage, only
 * for the member types a guardian can be assigned to, and only for this one permission. Nothing
 * else about the account is theirs to change.
 */
@Singleton
public class ManagedAccessService {
    private static final Logger log = LoggerFactory.getLogger(ManagedAccessService.class);

    /**
     * Members without an address of their own carry a synthetic one. It cannot receive mail, so it
     * is treated as no address at all.
     */
    private static final String SYNTHETIC_EMAIL_SUFFIX = ".local";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final MailLocaleService mailLocaleService;
    private final StationMemberService memberService;
    private final AuthService authService;
    private final EmailService emailService;

    @Inject
    public ManagedAccessService(
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            MailLocaleService mailLocaleService,
            StationMemberService memberService,
            AuthService authService,
            EmailService emailService) {
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.mailLocaleService = mailLocaleService;
        this.memberService = memberService;
        this.authService = authService;
        this.emailService = emailService;
    }

    /**
     * What a guardian sees and may change about the access of a member in their care.
     *
     * @param email        the address the account carries, or null when it is only a synthetic one
     * @param loginEnabled whether the member may sign in
     * @param canSignIn    whether granting access is possible at all, which needs a real address
     */
    public record ManagedAccess(String email, boolean loginEnabled, boolean canSignIn) {}

    /**
     * Reads the access state of a managed member.
     *
     * @param guardianMemberId the member acting as guardian
     * @param memberId         the member in their care
     * @return the address and whether signing in is switched on
     */
    public ManagedAccess get(int guardianMemberId, int memberId) {
        StationMember member = requireManaged(guardianMemberId, memberId);
        var account = account(member);
        String email = account.email();
        boolean real = isReal(email);
        return new ManagedAccess(real ? email : null, hasLogin(memberId), real);
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
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalised).matches() || !isReal(normalised)) {
            throw new BadRequestResponse("A valid email address is required");
        }
        var account = account(member);
        if (normalised.equalsIgnoreCase(account.email())) {
            return get(guardianMemberId, memberId);
        }
        var existing = accountRepository.findByEmail(normalised);
        if (existing.isPresent() && existing.get().id() != account.id()) {
            throw new BadRequestResponse("This email address already belongs to another account");
        }

        String previous = account.email();
        accountRepository.updateEmail(account.id(), normalised);
        accountRepository.deleteSessionsByAccount(account.id());
        if (isReal(previous)) {
            String mailLocale = mailLocaleService.forAccount(account.id());
            emailService.sendEmailChangedNotice(previous, account.firstName(), previous, normalised, mailLocale);
            emailService.sendEmailChangedNotice(normalised, account.firstName(), previous, normalised, mailLocale);
        }
        log.info(
                "Guardian {} set the email of managed member {} (account {})",
                guardianMemberId,
                memberId,
                account.id());
        return get(guardianMemberId, memberId);
    }

    /**
     * Switches signing in on or off for a managed member.
     *
     * <p>Switching it on needs an address to send the invitation to; switching it off ends the
     * sessions that are open, so the change is not merely cosmetic.
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
            if (!isReal(account.email())) {
                throw new BadRequestResponse("Set an email address before allowing this member to sign in");
            }
            if (!hasLogin(memberId)) {
                memberRepository.grantPermission(memberId, permission.id());
                if (accountRepository.findCredential(account.id()).isEmpty()) {
                    authService.sendPasswordSetup(account.id(), account.email(), account.firstName());
                }
            }
        } else if (hasLogin(memberId)) {
            memberRepository.revokePermission(memberId, permission.id());
            accountRepository.deleteSessionsByAccount(account.id());
        }
        log.info("Guardian {} set login of managed member {} to {}", guardianMemberId, memberId, enabled);
        return get(guardianMemberId, memberId);
    }

    private boolean hasLogin(int memberId) {
        return memberRepository.findPermissions(memberId).stream()
                .anyMatch(permission -> permission.permission() == StationPermission.LOGIN);
    }

    private static boolean isReal(String email) {
        return email != null
                && !email.isBlank()
                && !email.toLowerCase(Locale.ROOT).endsWith(SYNTHETIC_EMAIL_SUFFIX);
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
