/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.service.DocumentTemplateRequest;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.PAGE;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.cell;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.empty;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.group;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.letterhead;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.rows;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.signature;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.text;

/**
 * A letter template in every demo station: a certificate that a member belongs to the youth fire
 * brigade and takes part regularly, as a station hands it to a school.
 *
 * <p>It is laid out like a printed letter of a youth fire brigade: the station logo and the letterhead
 * across the top, a sender line, the date, the certificate with the member's values where a printed form
 * leaves gaps, a closing, the issuer's signature line, and three columns of contacts across the bottom.
 * Every name, address, number and domain in it is invented and reads as such, except the youth warden:
 * the template names a carer of the station as its issuer with her function, and the signature line
 * and the first column of contacts print her name and function from there.
 *
 * <p>It is written through {@link DocumentTemplateService} like a template a manager saves, so it passes
 * the same checks: every placeholder is one the station knows, the pronoun follows the gender question
 * the member band asks, and the signature line stands in the body. It is legal, since a certificate is
 * handed to others, and not offered for self service, since the youth warden issues and signs it.
 *
 * <p>The training times differ by group: the beginners train on Tuesdays, the advanced on Mondays. Each
 * time is a block of its own, shown only to members of its group, so one template prints the right day
 * for each member.
 *
 * <p>A station that already has a template of this name keeps it, so seeding twice leaves one.
 */
@Singleton
public class DemoDocumentTemplateSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoDocumentTemplateSeeder.class);

    /** What the certificate is called in the list of templates. */
    public static final String NAME = "Bescheinigung Mitgliedschaft";

    private static final String SENDER = "{{station.name}}, Musterstraße 1, 12345 Musterstadt";

    private static final String DATE = "Musterstadt, den {{today}}";

    private static final String TITLE =
            "**Bescheinigung über die Mitgliedschaft und regelmäßige Teilnahme in der Jugendfeuerwehr**";

    private static final String CERTIFICATE = """
            Sehr geehrte Damen und Herren,

            hiermit bestätige ich, dass {{member.fullName}}, geboren am {{member.birthDate}}, seit \
            {{member.joinDate|monthYear}} aktives Mitglied der {{station.name}} ist.""";

    private static final String BEGINNERS_GROUP = "Anfänger";

    private static final String ADVANCED_GROUP = "Fortgeschritten";

    private static final String BEGINNERS_PRACTICE = """
            {{member.firstName}} nimmt regelmäßig an unserem wöchentlichen Ausbildungs- und Übungsdienst \
            teil, der dienstags von 16:30 bis 18:00 Uhr stattfindet.""";

    private static final String ADVANCED_PRACTICE = """
            {{member.firstName}} nimmt regelmäßig an unserem wöchentlichen Ausbildungs- und Übungsdienst \
            teil, der montags von 17:30 bis 19:00 Uhr stattfindet.""";

    private static final String ENGAGEMENT = """
            Darüber hinaus engagiert {{pronoun.subject}} sich bei Veranstaltungen der Öffentlichkeitsarbeit sowie bei weiteren Diensten, Ausbildungstagen \
            und Wettbewerben, die zusätzlich, teils auch an Wochenenden, stattfinden. Der zeitliche Umfang \
            beträgt damit durchschnittlich etwa 4 Stunden pro Woche.

            Die Jugendfeuerwehr verbindet feuerwehrtechnische Ausbildung mit allgemeiner Jugendarbeit. Die \
            Jugendlichen erlernen neben fachlichen Grundlagen und Erster Hilfe vor allem Teamfähigkeit, \
            Verlässlichkeit und die Übernahme von Verantwortung. Die Mitgliedschaft ist ein verbindliches, \
            auf Dauer angelegtes ehrenamtliches Engagement mit regelmäßiger Anwesenheit.

            Ich bitte Sie, dieses Engagement bei der Planung zusätzlicher schulischer Verpflichtungen im \
            Nachmittagsbereich zu berücksichtigen.

            Für Rückfragen stehe ich Ihnen gern zur Verfügung.""";

    private static final String CLOSING = """
            Mit freundlichen Grüßen

            Musterstadt, den {{today}}""";

    private static final String SIGNER = """
            {{issuer.fullName}}\\
            {{issuer.function}} der {{station.name}}""";

    private static final String WARDEN = """
            {{issuer.function}}\\
            {{issuer.fullName}}\\
            jugendwart@example.org""";

    /** Where the youth warden who issues the certificate stands among the station's carers: Anna Schmidt. */
    static final int WARDEN_PLACE = 1;

    /** What the youth warden does, in the form that goes with her first name. */
    static final String WARDEN_FUNCTION = "Jugendfeuerwehrwartin";

    private static final String DEPUTY = """
            Stellvertretender Jugendfeuerwehrwart\\
            Max Mustermann\\
            stellvertretung@example.org""";

    private static final String ADDRESS = """
            Kreisjugendfeuerwehr Musterstadt\\
            {{station.name}}\\
            Musterstraße 1\\
            12345 Musterstadt

            Telefon: 0221 4710 123\\
            www.example.org""";

    private final DocumentTemplateService templates;
    private final MemberGroupRepository groups;

    @Inject
    public DemoDocumentTemplateSeeder(DocumentTemplateService templates, MemberGroupRepository groups) {
        this.templates = templates;
        this.groups = groups;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var owner = new Owner.Station(station.stationId());
        boolean present = templates.list(owner, false).stream().anyMatch(template -> NAME.equals(template.name()));
        if (present) return;
        int author = Objects.requireNonNull(
                station.adminMember().accountId(), "the station administrator is seeded with an account");
        var stationGroups = groups.findByStation(station.stationId());
        var warden = station.members().betreuer().get(WARDEN_PLACE);
        var template = templates.create(
                owner,
                certificate(
                        groupId(stationGroups, BEGINNERS_GROUP), groupId(stationGroups, ADVANCED_GROUP), warden.id()),
                author);
        log.info("Demo: Created document template {} for station {}", template.id(), station.stationId());
    }

    private static int groupId(List<MemberGroup> stationGroups, String name) {
        return stationGroups.stream()
                .filter(group -> name.equals(group.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the member band seeds the group " + name))
                .id();
    }

    /**
     * The certificate as the editor would send it.
     *
     * @param beginnersGroup the group whose members train on Tuesdays
     * @param advancedGroup  the group whose members train on Mondays
     * @param warden         the youth warden of the station, who issues and signs the certificate
     * @return the template
     */
    public static DocumentTemplateRequest certificate(int beginnersGroup, int advancedGroup, int warden) {
        return new DocumentTemplateRequest(
                DocumentTemplateKind.LETTER,
                NAME,
                null,
                null,
                null,
                false,
                null,
                true,
                false,
                false,
                null,
                null,
                DocumentLanguage.DE,
                warden,
                WARDEN_FUNCTION,
                letterhead(),
                rows(List.of(List.of(cell(37.5, text(WARDEN)), cell(37.5, text(DEPUTY)), cell(25, text(ADDRESS))))),
                rows(List.of(
                        List.of(cell(100, text(SENDER))),
                        List.of(cell(60, empty()), cell(40, text(DATE))),
                        List.of(cell(100, text(TITLE))),
                        List.of(cell(100, text(CERTIFICATE))),
                        List.of(cell(100, text(BEGINNERS_PRACTICE).shownTo(group(beginnersGroup)))),
                        List.of(cell(100, text(ADVANCED_PRACTICE).shownTo(group(advancedGroup)))),
                        List.of(cell(100, text(ENGAGEMENT))),
                        List.of(cell(100, text(CLOSING))),
                        List.of(cell(50, signature(SignatureRole.ISSUER, SIGNER, null)), cell(50, empty())))),
                PAGE,
                null,
                null);
    }
}
