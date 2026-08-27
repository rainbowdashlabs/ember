/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.service.MemberImportService.ColumnMapping;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reading a list of people out of a spreadsheet.
 *
 * <p>Two things went wrong here and both were quiet. An answer written into a profile is stored as
 * JSON, and the import handed the cell over as it stood, so a telephone number or a surname ended the
 * whole import in an error from the database. And a list read a second time is the ordinary way a
 * station keeps up to date, which only works if the second reading recognises the people from the
 * first.
 */
class MemberImportServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static MemberImportService service;
    private static Station station;

    @BeforeAll
    static void setup() {
        service = new MemberImportService(
                accountRepo, stationMemberRepo, memberGroupRepo, profileFieldRepo, new PasswordHasher());
        station = stationRepo.create("ImportStation");
    }

    @BeforeEach
    void clean() {
        for (var member : stationMemberRepo.findByStation(station.id(), true)) {
            var account = member.accountId();
            stationMemberRepo.delete(member.id());
            accountRepo.delete(account);
        }
    }

    @AfterEach
    void tidy() {
        clean();
    }

    private ColumnMapping map(String column, String target) {
        return new ColumnMapping(column, target, 0, " ", Map.of(), "", 0);
    }

    /** A question of this station, named apart from every other test's so the station can hold them all. */
    private int field(String name, ProfileFieldType type) {
        String unique = name + " " + NAMES.incrementAndGet();
        return profileFieldRepo
                .create(station.id(), unique, type, ProfileFieldConfig.empty(), 99, ProfileFieldScope.MEMBER)
                .id();
    }

    private String storedValue(int memberId, int fieldId) {
        return profileFieldRepo.findValues(memberId).stream()
                .filter(value -> value.fieldId() == fieldId)
                .map(value -> value.value())
                .findFirst()
                .orElseThrow(() -> new AssertionError("nothing was written for field " + fieldId));
    }

    private int guardian() {
        return stationMemberRepo.findByStation(station.id()).stream()
                .filter(member -> member.userType() == StationUserType.GUARDIAN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("nobody joined as a guardian"))
                .id();
    }

    private int onlyMember() {
        var members = stationMemberRepo.findByStation(station.id());
        assertEquals(1, members.size(), "exactly one person was read in");
        return members.getFirst().id();
    }

    /**
     * A telephone number is text, whatever it looks like.
     *
     * <p>01700000000 is not a JSON value: it reads as a number with a leading zero, which JSON does
     * not allow, and the database refused the whole import over it. The reported fault, and this is
     * its guard.
     */
    @Test
    void aPhoneNumberSurvivesBeingImported() {
        int phone = field("Mobilnummer", ProfileFieldType.TEXT);
        String csv = "Vorname;Name;Telefon\nMax;Müller;01700000000\n";

        var result = service.importMembers(
                station.id(),
                csv,
                ";",
                List.of(map("Vorname", "firstName"), map("Name", "lastName"), map("Telefon", "field:" + phone)),
                List.of());

        assertEquals(1, result.membersCreated());
        assertEquals("\"01700000000\"", storedValue(onlyMember(), phone), "kept as the text it is");
    }

    /** A surname is not JSON either, and quotes inside one must not break the document. */
    @Test
    void textWithQuotesIsStoredAsText() {
        int nickname = field("Spitzname", ProfileFieldType.TEXT);
        String csv = "Vorname;Name;Spitzname\nMax;Müller;der \"Lange\"\n";

        service.importMembers(
                station.id(),
                csv,
                ";",
                List.of(map("Vorname", "firstName"), map("Name", "lastName"), map("Spitzname", "field:" + nickname)),
                List.of());

        assertEquals("\"der \\\"Lange\\\"\"", storedValue(onlyMember(), nickname));
    }

    /** What a cell means follows the question it answers. */
    @Test
    void datesNumbersAndYesNoAreStoredAsWhatTheyAre() {
        int birthday = field("Geburtstag", ProfileFieldType.DATE);
        int shoes = field("Schuhgröße", ProfileFieldType.NUMBER);
        int juleica = field("Juleica", ProfileFieldType.BOOLEAN);
        String csv = "Vorname;Name;Geburtstag;Schuhe;Juleica\nMax;Müller;04.03.2011;42;Ja\n";

        service.importMembers(
                station.id(),
                csv,
                ";",
                List.of(
                        map("Vorname", "firstName"),
                        map("Name", "lastName"),
                        map("Geburtstag", "field:" + birthday),
                        map("Schuhe", "field:" + shoes),
                        map("Juleica", "field:" + juleica)),
                List.of());

        int member = onlyMember();
        assertEquals("\"2011-03-04\"", storedValue(member, birthday), "a German date is stored as an ISO one");
        assertEquals("42", storedValue(member, shoes));
        assertEquals("true", storedValue(member, juleica));
    }

    /** Spaces around a cell are how a spreadsheet looks, not part of the answer. */
    @Test
    void spacesAroundACellAreNotPartOfIt() {
        int nickname = field("Spitzname", ProfileFieldType.TEXT);
        String csv = "Vorname;Name;Spitzname\n  Max  ;  Müller  ;  Maxi  \n";

        service.importMembers(
                station.id(),
                csv,
                ";",
                List.of(map("Vorname", "firstName"), map("Name", "lastName"), map("Spitzname", "field:" + nickname)),
                List.of());

        int member = onlyMember();
        var account = accountRepo
                .findById(stationMemberRepo.findById(member).orElseThrow().accountId())
                .orElseThrow();
        assertEquals("Max", account.firstName());
        assertEquals("Müller", account.lastName());
        assertEquals("\"Maxi\"", storedValue(member, nickname));
    }

    /** The same list read twice leaves one of each, matched on the address. */
    @Test
    void aSecondReadingRecognisesPeopleByTheirAddress() {
        String csv = "Vorname;Name;Email\nMax;Müller;max@example.com\n";
        var mappings = List.of(map("Vorname", "firstName"), map("Name", "lastName"), map("Email", "email"));

        service.importMembers(station.id(), csv, ";", mappings, List.of());
        var again = service.importMembers(station.id(), csv, ";", mappings, List.of());

        assertEquals(0, again.membersCreated(), "nobody new the second time");
        assertEquals(1, stationMemberRepo.findByStation(station.id()).size());
        assertFalse(again.warnings().isEmpty(), "and it says who was passed over");
    }

    /**
     * Without an address, the name decides, and only within this station.
     *
     * <p>A youth list rarely carries addresses for the children on it. Read twice without this, every
     * child would be on the books twice over.
     */
    @Test
    void aSecondReadingRecognisesPeopleByTheirNameWhenNoAddressIsGiven() {
        String csv = "Vorname;Name\nMax;Müller\n";
        var mappings = List.of(map("Vorname", "firstName"), map("Name", "lastName"));

        service.importMembers(station.id(), csv, ";", mappings, List.of());
        var again = service.importMembers(station.id(), "Vorname;Name\n  max  ;  MÜLLER  \n", ";", mappings, List.of());

        assertEquals(0, again.membersCreated(), "spelling and spacing do not make a second person");
        assertEquals(1, stationMemberRepo.findByStation(station.id()).size());
    }

    /** A row struck out in the preview is walked past. */
    @Test
    void aRowStruckOutIsNotImported() {
        String csv = "Vorname;Name\nMax;Müller\nLena;Fischer\n";
        var mappings = List.of(map("Vorname", "firstName"), map("Name", "lastName"));

        var result = service.importMembers(station.id(), csv, ";", mappings, List.of(0));

        assertEquals(1, result.membersCreated());
        var members = stationMemberRepo.findByStation(station.id());
        var account = accountRepo.findById(members.getFirst().accountId()).orElseThrow();
        assertEquals("Lena", account.firstName(), "the first row was the one struck out");
    }

    /**
     * The telephone number of a parent is text as much as the member's own is.
     *
     * <p>The reported fault, and the second of its kind: the member's own answers were put through
     * the conversion, the parent's telephone number was written straight to the database and ended
     * the reading in an error. A youth list carries a parent on nearly every row, so this was the
     * common case rather than the rare one.
     */
    @Test
    void theTelephoneNumberOfAParentSurvivesToo() {
        int phone = profileFieldRepo
                .create(
                        station.id(),
                        "Mobilnummer",
                        ProfileFieldType.TEXT,
                        ProfileFieldConfig.empty(),
                        99,
                        ProfileFieldScope.GUARDIAN)
                .id();
        String csv = "Vorname;Name;Kontakt;Telefon;Kontakt Email\n"
                + "Lena;Sommer;Rita Sommer;01700000000;rita@example.com\n";

        var result = service.importMembers(
                station.id(),
                csv,
                ";",
                List.of(
                        map("Vorname", "firstName"),
                        map("Name", "lastName"),
                        map("Kontakt", "manager:1:firstName"),
                        map("Telefon", "manager:1:phone"),
                        map("Kontakt Email", "manager:1:email")),
                List.of());

        assertEquals(1, result.membersCreated());
        assertEquals(1, result.managersCreated(), "the parent joined as a guardian");
        assertEquals("\"01700000000\"", storedValue(guardian(), phone), "kept as the text it is");
        assertEquals(1, result.managersLinked(), "and was linked to their child");
    }

    /**
     * A parent the list gives no address for is a parent all the same.
     *
     * <p>A contact without one used to be dropped where it stood: nobody written down, nobody linked,
     * and nothing said about it. A youth list gives a telephone number far more often than an address.
     */
    @Test
    void aParentWithoutAnAddressIsStillWrittenDown() {
        String csv = "Vorname;Name;Kontakt;Telefon\nLena;Sommer;Rita Sommer;01700000000\n";

        var result = service.importMembers(
                station.id(),
                csv,
                ";",
                List.of(
                        map("Vorname", "firstName"),
                        map("Name", "lastName"),
                        map("Kontakt", "manager:1:firstName"),
                        map("Telefon", "manager:1:phone")),
                List.of());

        assertEquals(1, result.managersCreated());
        assertEquals(1, result.managersLinked());
    }

    /**
     * A contact given in one column is two names, not one.
     *
     * <p>The wizard points a column headed "Kontakt" at the given name by itself, which left the
     * surname empty and the child's standing in for it: "Rita Sommer Sommer".
     */
    @Test
    void aWholeNameInOneColumnIsReadAsTwo() {
        String csv = "Vorname;Name;Kontakt\nLena;Sommer;Rita Sommer\n";

        service.importMembers(
                station.id(),
                csv,
                ";",
                List.of(map("Vorname", "firstName"), map("Name", "lastName"), map("Kontakt", "manager:1:firstName")),
                List.of());

        var account = accountRepo
                .findById(stationMemberRepo.findById(guardian()).orElseThrow().accountId())
                .orElseThrow();
        assertEquals("Rita", account.firstName());
        assertEquals("Sommer", account.lastName());
    }

    /**
     * Whatever a question is for, the cell answering it reaches the database.
     *
     * <p>An answer is held as JSON and a cell is not JSON, so every kind of question has to be
     * converted before it is stored. This walks all of them with a cell that is awkward for each: a
     * leading zero is not a JSON number, and it is neither a date nor a yes.
     */
    @Test
    void everyKindOfQuestionTakesAnAwkwardCell() {
        var types = Arrays.stream(ProfileFieldType.values())
                .filter(ProfileFieldType::holdsValue)
                .toList();
        var mappings = new ArrayList<ColumnMapping>(List.of(map("Vorname", "firstName"), map("Name", "lastName")));
        var fieldsByType = new LinkedHashMap<ProfileFieldType, Integer>();
        var header = new StringBuilder("Vorname;Name");
        var row = new StringBuilder("Max;Müller");
        for (var type : types) {
            int id = field(type.name(), type);
            fieldsByType.put(type, id);
            header.append(';').append(type.name());
            row.append(";01700000000");
            mappings.add(map(type.name(), "field:" + id));
        }

        var result = service.importMembers(station.id(), header + "\n" + row + "\n", ";", mappings, List.of());

        assertEquals(1, result.membersCreated());
        int member = onlyMember();
        for (var entry : fieldsByType.entrySet()) {
            assertNotNull(storedValue(member, entry.getValue()), "a " + entry.getKey() + " question kept its answer");
        }
    }

    /** The preview says where each row came from, which is what striking one out refers to. */
    @Test
    void thePreviewNumbersItsRowsAndMarksTheStruckOutOnes() {
        String csv = "Vorname;Name\nMax;Müller\nLena;Fischer\n";
        var mappings = List.of(map("Vorname", "firstName"), map("Name", "lastName"));

        var preview = service.preview(station.id(), csv, ";", mappings, List.of(1));

        assertEquals(2, preview.members().size(), "both are shown, one of them struck out");
        assertEquals(0, preview.members().getFirst().row());
        assertFalse(preview.members().getFirst().ignored());
        assertTrue(preview.members().get(1).ignored());
    }
}
