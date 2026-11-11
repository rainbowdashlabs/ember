/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.feature.account.entity.AccountListQuery;
import dev.chojo.ember.feature.account.entity.AccountOverview;
import dev.chojo.ember.feature.account.entity.AccountOverview.AssociationRole;
import dev.chojo.ember.feature.account.entity.AccountPasswordState;
import dev.chojo.ember.feature.account.entity.AccountSort;
import dev.chojo.ember.feature.account.entity.SetupMailKind;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account list as the database answers it: one row per account with what it belongs to and how
 * it signs in, searched, filtered, sorted and paged in the query, with the count always agreeing with
 * the pages.
 */
class AccountOverviewRepositoryTest extends RepositoryTestBase {
    private final AccountOverviewRepository repository = new AccountOverviewRepository();

    private AccountOverview only(String search) {
        var rows = repository.page(AccountListQuery.search(search), 10, 0);
        assertEquals(1, rows.size(), "exactly one account answers " + search);
        return rows.getFirst();
    }

    /**
     * The register names the query finds, in the order it answers them, after checking that the
     * count says the same number.
     */
    private List<String> names(Query query) {
        var built = query.build();
        var rows = repository.page(built, 50, 0);
        assertEquals(rows.size(), repository.count(built), "the count agrees with the page");
        return rows.stream().map(AccountOverview::name).toList();
    }

    private int account(String marker, String first, String last) {
        return accountRepo
                .create(marker + first + "@test.com", first, last + " " + marker, true)
                .id();
    }

    private void signedInAt(int accountId, Instant at) {
        query("UPDATE account SET last_sign_in_at = :at WHERE id = :id;")
                .single(call().bind("at", at, INSTANT_TIMESTAMP).bind("id", accountId))
                .update();
    }

    private void passkeys(int accountId, int count) {
        for (int i = 0; i < count; i++) {
            var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Passkey " + i);
            twoFactorRepo.createWebAuthn(
                    factor.id(),
                    ("ovw-" + factor.id()).getBytes(),
                    new byte[] {1},
                    0,
                    null,
                    List.of("internal"),
                    "none",
                    new byte[64],
                    true,
                    false,
                    true,
                    true);
        }
    }

    @Test
    void anAccountShowsItsStationsAssociationsAndHowItSignsIn() {
        String marker = "ovw" + System.nanoTime();
        var account = accountRepo.create(marker + "@test.com", "Lena", "Weber", true);
        var home = stationRepo.create("Overview Home " + marker);
        var left = stationRepo.create("Overview Left " + marker);
        stationMemberRepo.create(home.id(), account.id());
        stationMemberRepo.setFormer(
                stationMemberRepo.create(left.id(), account.id()).id(), true);
        var association = clusterRepo.create("Overview Association " + marker, null, home.id());
        clusterRepo.addMember(association.id(), account.id(), ClusterUserType.CLUSTER_ADMIN);
        Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        accountRepo.setOneTimePassword(account.id(), "{otp}", deadline);
        var factors = new TwoFactorRepository();
        factors.createFactor(account.id(), TwoFactorKind.TOTP, "phone");

        var row = only(marker);

        assertEquals(account.id(), row.id());
        assertEquals("Lena Weber", row.name());
        assertEquals(marker + "@test.com", row.email());
        assertEquals(marker + "@test.com", row.loginName());
        assertEquals(InstanceUserType.USER, row.instanceUserType());
        assertNull(row.lastSignInAt());
        assertEquals(List.of(home.name()), row.stations(), "a station left behind is not listed");
        assertEquals(
                List.of(new AssociationRole(association.name(), ClusterUserType.CLUSTER_ADMIN)), row.associations());
        assertTrue(row.hasPassword());
        assertTrue(row.passwordSignIn());
        assertEquals(deadline, row.oneTimePasswordExpiresAt());
        assertEquals(0, row.passkeys());
        assertTrue(row.twoFactor());
        assertEquals(Boolean.TRUE, row.confirmed());
        assertEquals(SetupMailKind.INVITATION, row.setupMail(), "a one-time password is no password of its own");
    }

    @Test
    void anAccountWithoutAddressOrPasswordSaysSo() {
        String marker = "ovwname" + System.nanoTime();
        var account = accountRepo.create(marker + "@station.local", "Kim", "Ohne", true);
        accountRepo.updateUsername(account.id(), marker);

        var row = only(marker);

        assertNull(row.email(), "a made-up address is no address");
        assertEquals(marker, row.loginName());
        assertFalse(row.hasPassword());
        assertFalse(row.passwordSignIn());
        assertFalse(row.twoFactor());
        assertTrue(row.stations().isEmpty());
        assertTrue(row.associations().isEmpty());
        assertNull(row.confirmed(), "a made-up address is neither confirmed nor waiting to be");
    }

    @Test
    void theMailAnAccountWaitsForFollowsItsPasswordAndItsAddress() {
        String marker = "ovwmail" + System.nanoTime();
        int unconfirmed = accountRepo
                .create(marker + "a@test.com", "Una", "Bestätigt", false)
                .id();
        accountRepo.createCredential(unconfirmed, "hash");
        accountRepo.markSetupCompleted(unconfirmed);
        int ready = accountRepo.create(marker + "b@test.com", "Rea", "Dy", true).id();
        accountRepo.createCredential(ready, "hash");
        int setUpWithoutPassword = accountRepo
                .create(marker + "c@test.com", "Ohne", "Passwort", true)
                .id();
        accountRepo.markSetupCompleted(setUpWithoutPassword);

        assertEquals(SetupMailKind.CONFIRMATION, only(marker + "a").setupMail());
        assertEquals(Boolean.FALSE, only(marker + "a").confirmed());
        assertNull(only(marker + "b").setupMail());
        assertNull(only(marker + "c").setupMail(), "an account set up by passkey needs no invitation");
    }

    @Test
    void theSearchAndThePagesAreAnsweredByTheDatabase() {
        String marker = "ovwpage" + System.nanoTime();
        for (int i = 0; i < 3; i++) {
            accountRepo.create(marker + i + "@test.com", "Page", "Person " + i, true);
        }

        assertEquals(3, repository.count(AccountListQuery.search(marker)));
        assertEquals(2, repository.page(AccountListQuery.search(marker), 2, 0).size());
        assertEquals(1, repository.page(AccountListQuery.search(marker), 2, 2).size());
        assertEquals(
                "Page Person 2",
                repository
                        .page(AccountListQuery.search(marker), 2, 2)
                        .getFirst()
                        .name());
        assertTrue(repository.count(AccountListQuery.search(null)) >= 3);
        assertEquals(0, repository.count(AccountListQuery.search(marker + "nobody")));
    }

    @Test
    void theRoleFilterReachesBeyondThePage() {
        String marker = "ovwrole" + System.nanoTime();
        account(marker, "Anna", "User");
        int admin = account(marker, "Bert", "Admin");
        accountRepo.setInstanceUserType(admin, InstanceUserType.ADMINISTRATOR);

        assertEquals(List.of("Bert Admin " + marker), names(new Query(marker).roles(InstanceUserType.ADMINISTRATOR)));
        assertEquals(
                2,
                names(new Query(marker).roles(InstanceUserType.ADMINISTRATOR, InstanceUserType.USER))
                        .size());
    }

    @Test
    void thePasswordFilterTellsTheFourStatesApart() {
        String marker = "ovwpw" + System.nanoTime();
        account(marker, "None", "Pw");
        int on = account(marker, "On", "Pw");
        accountRepo.createCredential(on, "hash");
        int off = account(marker, "Off", "Pw");
        accountRepo.createCredential(off, "hash");
        accountRepo.setPasswordLoginDisabled(off, true);
        int oneTime = account(marker, "Once", "Pw");
        accountRepo.setOneTimePassword(oneTime, "{otp}", Instant.now().plus(1, ChronoUnit.DAYS));

        assertEquals(List.of("None Pw " + marker), names(new Query(marker).passwords(AccountPasswordState.NONE)));
        assertEquals(List.of("On Pw " + marker), names(new Query(marker).passwords(AccountPasswordState.ON)));
        assertEquals(List.of("Off Pw " + marker), names(new Query(marker).passwords(AccountPasswordState.OFF)));
        assertEquals(List.of("Once Pw " + marker), names(new Query(marker).passwords(AccountPasswordState.ONE_TIME)));
        assertEquals(
                2,
                names(new Query(marker).passwords(AccountPasswordState.ON, AccountPasswordState.OFF))
                        .size());
    }

    @Test
    void theSecondFactorAndPasskeyFiltersReadWhatTheColumnsShow() {
        String marker = "ovw2fa" + System.nanoTime();
        int totp = account(marker, "Totp", "Factor");
        twoFactorRepo.createFactor(totp, TwoFactorKind.TOTP, "phone");
        int twoKeys = account(marker, "Two", "Keys");
        passkeys(twoKeys, 2);
        account(marker, "Bare", "Account");

        assertEquals(List.of("Totp Factor " + marker), names(new Query(marker).twoFactor(true)));
        assertEquals(2, names(new Query(marker).twoFactor(false)).size(), "a sign-in passkey is no second factor");
        assertEquals(List.of("Two Keys " + marker), names(new Query(marker).passkeys(1, null)));
        assertEquals(List.of("Two Keys " + marker), names(new Query(marker).passkeys(2, 2)));
        assertEquals(2, names(new Query(marker).passkeys(null, 0)).size());
    }

    @Test
    void confirmedCountsRealAddressesOnly() {
        String marker = "ovwconf" + System.nanoTime();
        accountRepo.create(marker + "yes@test.com", "Yes", "Confirmed " + marker, true);
        accountRepo.create(marker + "no@test.com", "No", "Confirmed " + marker, false);
        accountRepo.create(marker + "@station.local", "Stand", "In " + marker, false);

        assertEquals(List.of("Yes Confirmed " + marker), names(new Query(marker).confirmed(true)));
        assertEquals(List.of("No Confirmed " + marker), names(new Query(marker).confirmed(false)));
        assertEquals(3, names(new Query(marker)).size());
        assertEquals(List.of("Stand In " + marker), names(new Query(marker).withoutAddress()));
        assertEquals(
                List.of("No Confirmed " + marker, "Stand In " + marker),
                names(new Query(marker).confirmed(false).withoutAddress()));
    }

    @Test
    void theLastSignInIsFilteredByDayAndByNever() {
        String marker = "ovwsign" + System.nanoTime();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = LocalDate.of(2026, 3, 14);
        signedInAt(
                account(marker, "Early", "Sign"),
                day.minusDays(3).atTime(12, 0).atZone(zone).toInstant());
        signedInAt(
                account(marker, "Middle", "Sign"),
                day.atTime(12, 0).atZone(zone).toInstant());
        signedInAt(
                account(marker, "Late", "Sign"),
                day.plusDays(3).atTime(12, 0).atZone(zone).toInstant());
        account(marker, "Never", "Sign");

        assertEquals(List.of("Middle Sign " + marker), names(new Query(marker).signedIn(day, day, false)));
        assertEquals(
                List.of("Late Sign " + marker, "Middle Sign " + marker),
                names(new Query(marker).signedIn(day, null, false)));
        assertEquals(
                List.of("Early Sign " + marker, "Middle Sign " + marker),
                names(new Query(marker).signedIn(null, day, false)));
        assertEquals(List.of("Never Sign " + marker), names(new Query(marker).signedIn(null, null, true)));
        assertEquals(
                List.of("Middle Sign " + marker, "Never Sign " + marker),
                names(new Query(marker).signedIn(day, day, true)),
                "a range and never are alternatives, as an empty cell and a range are in a date filter");
    }

    @Test
    void aDayIsTheCalendarDayOfTheInstancesZone() {
        String marker = "ovwzone" + System.nanoTime();
        ZoneId farEast = ZoneId.of("Pacific/Kiritimati");
        var farEastRepository = new AccountOverviewRepository(farEast);
        LocalDate day = LocalDate.of(2026, 3, 14);
        signedInAt(
                account(marker, "Just", "After"),
                day.atTime(0, 30).atZone(farEast).toInstant());
        signedInAt(
                account(marker, "Barely", "Before"),
                day.minusDays(1).atTime(23, 30).atZone(farEast).toInstant());
        signedInAt(
                account(marker, "Late", "Night"),
                day.atTime(23, 30).atZone(farEast).toInstant());
        var query = new Query(marker).signedIn(day, day, false).build();

        var names = farEastRepository.page(query, 10, 0).stream()
                .map(AccountOverview::name)
                .toList();

        assertEquals(
                List.of("Just After " + marker, "Late Night " + marker),
                names,
                "half past midnight there is still the day before in most of the world");
        assertEquals(2, farEastRepository.count(query));
    }

    @Test
    void stationsAndAssociationsFilterByUidOrByNone() {
        String marker = "ovwplace" + System.nanoTime();
        var north = stationRepo.create("North " + marker);
        var south = stationRepo.create("South " + marker);
        int inNorth = account(marker, "Nora", "North");
        stationMemberRepo.create(north.id(), inNorth);
        int inSouth = account(marker, "Sven", "South");
        stationMemberRepo.create(south.id(), inSouth);
        int formerly = account(marker, "Fritz", "Former");
        stationMemberRepo.setFormer(
                stationMemberRepo.create(north.id(), formerly).id(), true);
        var association = clusterRepo.create("Assoc " + marker, null, north.id());
        clusterRepo.addMember(association.id(), inSouth, ClusterUserType.CLUSTER_ADMIN);

        assertEquals(List.of("Nora North " + marker), names(new Query(marker).stations(Set.of(north.uid()), false)));
        assertEquals(
                List.of("Fritz Former " + marker, "Nora North " + marker),
                names(new Query(marker).stations(Set.of(north.uid()), true)),
                "a member who left counts as in no station");
        assertEquals(List.of("Fritz Former " + marker), names(new Query(marker).stations(Set.of(), true)));
        assertEquals(
                List.of("Sven South " + marker),
                names(new Query(marker).associations(Set.of(association.uid()), false)));
        assertEquals(2, names(new Query(marker).associations(Set.of(), true)).size());
    }

    @Test
    void theOrderIsTheDatabasesAcrossPages() {
        String marker = "ovwsort" + System.nanoTime();
        int anna = account(marker, "Anna", "Sort");
        int bert = account(marker, "Bert", "Sort");
        account(marker, "Carl", "Sort");
        signedInAt(anna, Instant.now().minus(2, ChronoUnit.DAYS));
        signedInAt(bert, Instant.now().minus(1, ChronoUnit.DAYS));
        passkeys(anna, 1);

        assertEquals(
                List.of("Carl Sort " + marker, "Bert Sort " + marker, "Anna Sort " + marker),
                names(new Query(marker).sort(AccountSort.NAME, true)));
        assertEquals(
                List.of("Bert Sort " + marker, "Anna Sort " + marker, "Carl Sort " + marker),
                names(new Query(marker).sort(AccountSort.LAST_SIGN_IN, true)),
                "never signed in comes last either way");
        assertEquals(
                List.of("Anna Sort " + marker, "Bert Sort " + marker, "Carl Sort " + marker),
                names(new Query(marker).sort(AccountSort.LAST_SIGN_IN, false)));
        assertEquals(
                List.of("Anna Sort " + marker, "Bert Sort " + marker, "Carl Sort " + marker),
                names(new Query(marker).sort(AccountSort.PASSKEYS, true)));
        var secondPage = repository.page(
                new Query(marker).sort(AccountSort.LAST_SIGN_IN, true).build(), 2, 2);
        assertEquals(
                List.of("Carl Sort " + marker),
                secondPage.stream().map(AccountOverview::name).toList());
    }

    /** One account list query, built a part at a time with everything else left open. */
    private static final class Query {
        private final String search;
        private Set<InstanceUserType> roles = Set.of();
        private Set<AccountPasswordState> passwords = Set.of();
        private @Nullable Boolean twoFactor;
        private @Nullable Integer passkeysMin;
        private @Nullable Integer passkeysMax;
        private @Nullable Boolean confirmed;
        private boolean withoutAddress;
        private @Nullable LocalDate signedInFrom;
        private @Nullable LocalDate signedInTo;
        private boolean neverSignedIn;
        private Set<UUID> stations = Set.of();
        private boolean withoutStation;
        private Set<UUID> associations = Set.of();
        private boolean withoutAssociation;
        private AccountSort sort = AccountSort.NAME;
        private boolean descending;

        private Query(String search) {
            this.search = search;
        }

        Query roles(InstanceUserType... values) {
            roles = Set.of(values);
            return this;
        }

        Query passwords(AccountPasswordState... values) {
            passwords = Set.of(values);
            return this;
        }

        Query twoFactor(boolean value) {
            twoFactor = value;
            return this;
        }

        Query passkeys(@Nullable Integer min, @Nullable Integer max) {
            passkeysMin = min;
            passkeysMax = max;
            return this;
        }

        Query withoutAddress() {
            withoutAddress = true;
            return this;
        }

        Query confirmed(boolean value) {
            confirmed = value;
            return this;
        }

        Query signedIn(@Nullable LocalDate from, @Nullable LocalDate to, boolean never) {
            signedInFrom = from;
            signedInTo = to;
            neverSignedIn = never;
            return this;
        }

        Query stations(Set<UUID> uids, boolean orNone) {
            stations = uids;
            withoutStation = orNone;
            return this;
        }

        Query associations(Set<UUID> uids, boolean orNone) {
            associations = uids;
            withoutAssociation = orNone;
            return this;
        }

        Query sort(AccountSort by, boolean down) {
            sort = by;
            descending = down;
            return this;
        }

        AccountListQuery build() {
            return new AccountListQuery(
                    search,
                    roles,
                    passwords,
                    twoFactor,
                    passkeysMin,
                    passkeysMax,
                    confirmed,
                    withoutAddress,
                    signedInFrom,
                    signedInTo,
                    neverSignedIn,
                    stations,
                    withoutStation,
                    associations,
                    withoutAssociation,
                    sort,
                    descending);
        }
    }
}
