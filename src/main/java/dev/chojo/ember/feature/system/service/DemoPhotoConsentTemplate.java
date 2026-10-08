/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.content.entity.GuardianCondition;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TemplateSigning;
import dev.chojo.ember.feature.generator.service.DocumentTemplateRequest;

import java.util.List;

import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.PAGE;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.cell;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.empty;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.letterhead;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.rows;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.signature;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.text;
import static dev.chojo.ember.feature.system.service.DemoLetterBlocks.userType;

/**
 * The photo consent the demo's citizens' festival asks every participant to bring: a letter in German that
 * names the appointment, its day, time and place, says which photos and videos are taken there and where
 * they are published, and that consenting is voluntary and can be withdrawn at any time for the future.
 *
 * <p>The participant signs it, and so does every guardian of a young member, whose lines and the sentence
 * about them are printed only for the members of the youth fire brigade. The youth warden takes the consent
 * in for the station and signs as its issuer. Each line names what its signer declares. Signed copies are kept
 * for two years after the member has left and go out to the signers with the sealed PDF attached.
 *
 * <p>It is marked for appointments, which makes it legal, and is not offered for self service, since a
 * participant gets it through the appointment.
 */
final class DemoPhotoConsentTemplate {

    /** What the consent is called in the list of templates and on the appointment. */
    static final String NAME = "Fotoerlaubnis Bürgerfest";

    /** How long signed consents are kept after the member has left: two years. */
    static final int RETENTION_MONTHS = 24;

    /** What the participant declares. */
    static final String PARTICIPANT_STATEMENT = "Ich willige in Foto- und Videoaufnahmen beim Bürgerfest ein.";

    /** What each guardian declares. */
    static final String GUARDIAN_STATEMENT =
            "Als erziehungsberechtigte Person willige ich in Foto- und Videoaufnahmen meines Kindes beim Bürgerfest ein.";

    /** What the youth warden declares on taking the consent in. */
    static final String ISSUER_STATEMENT = "Ich nehme die Einwilligung für die Jugendfeuerwehr entgegen.";

    private static final String TITLE = "**Einwilligung in Foto- und Videoaufnahmen beim Bürgerfest**";

    private static final String APPOINTMENT = """
            Veranstaltung: {{event.name}}\\
            Datum: {{event.start|weekday}}\\
            Zeit: {{event.start|time}} bis {{event.end|time}} Uhr\\
            Ort: {{event.location}}\\
            Teilnehmende Person: {{member.fullName}}""";

    private static final String RECORDINGS = """
            Beim Bürgerfest zeigt die {{station.name}} eine Löschvorführung, betreut die Spritzwand für \
            Kinder und informiert an ihrem Stand. Dabei entstehen Fotos und kurze Videos von den \
            Vorführungen, vom Stand und als Gruppenbilder der Jugendfeuerwehr. Aufgenommen werden sie von \
            den Betreuerinnen und Betreuern der Jugendfeuerwehr und von der örtlichen Presse.""";

    private static final String PUBLICATION = """
            Veröffentlicht werden die Aufnahmen auf der Internetseite der {{station.name}}, in ihren \
            Auftritten in sozialen Medien und in der örtlichen Presse. Zu den Aufnahmen werden keine \
            vollständigen Namen genannt.""";

    private static final String VOLUNTARY = """
            Die Einwilligung ist freiwillig. Wer sie nicht erteilt, nimmt trotzdem am Bürgerfest teil. Die \
            Jugendfeuerwehr achtet dann darauf, dass die Person auf veröffentlichten Aufnahmen nicht zu \
            erkennen ist.""";

    private static final String WITHDRAWAL = """
            Die Einwilligung kann jederzeit mit Wirkung für die Zukunft widerrufen werden, formlos bei \
            {{issuer.fullName}} ({{issuer.function}}) oder per E-Mail an jugendwart@example.org. Nach einem \
            Widerruf werden die Aufnahmen von der Internetseite und aus den sozialen Medien entfernt. Was \
            bis dahin bereits gedruckt erschienen ist, lässt sich nicht mehr zurückholen.""";

    private static final String ONE_GUARDIAN = """
            Für Minderjährige willigt zusätzlich die erziehungsberechtigte Person ein: \
            {{guardian1.fullName}}.""";

    private static final String TWO_GUARDIANS = """
            Für Minderjährige willigen zusätzlich beide erziehungsberechtigten Personen ein: \
            {{guardian1.fullName}} und {{guardian2.fullName}}.""";

    private static final String PLACE_AND_DATE = "Musterstadt, den {{today}}";

    private static final String PARTICIPANT = "{{member.fullName}}";

    private static final String GUARDIAN = "Erziehungsberechtigte Person";

    private static final String ISSUER = """
            {{issuer.fullName}}\\
            {{issuer.function}} der {{station.name}}""";

    private DemoPhotoConsentTemplate() {}

    /**
     * The consent as the editor would send it.
     *
     * @param warden the youth warden of the station, who issues the consent and takes it in
     * @return the template
     */
    static DocumentTemplateRequest request(int warden) {
        var minors = userType(StationUserType.MEMBER);
        return new DocumentTemplateRequest(
                DocumentTemplateKind.LETTER,
                NAME,
                null,
                null,
                null,
                false,
                null,
                true,
                true,
                false,
                null,
                null,
                DocumentLanguage.DE,
                warden,
                DemoDocumentTemplateSeeder.WARDEN_FUNCTION,
                letterhead(),
                null,
                rows(List.of(
                        List.of(cell(60, empty()), cell(40, text(PLACE_AND_DATE))),
                        List.of(cell(100, text(TITLE))),
                        List.of(cell(100, text(APPOINTMENT))),
                        List.of(cell(100, text(RECORDINGS))),
                        List.of(cell(100, text(PUBLICATION))),
                        List.of(cell(100, text(VOLUNTARY))),
                        List.of(cell(100, text(WITHDRAWAL))),
                        List.of(cell(
                                100, text(ONE_GUARDIAN).shownTo(minors).when(GuardianCondition.NO_SECOND_GUARDIAN))),
                        List.of(cell(100, text(TWO_GUARDIANS).shownTo(minors).when(GuardianCondition.SECOND_GUARDIAN))),
                        List.of(
                                cell(50, signature(SignatureRole.PARTICIPANT, PARTICIPANT, PARTICIPANT_STATEMENT)),
                                cell(50, signature(SignatureRole.ISSUER, ISSUER, ISSUER_STATEMENT))),
                        List.of(cell(
                                100,
                                signature(SignatureRole.EACH_GUARDIAN, GUARDIAN, GUARDIAN_STATEMENT)
                                        .shownTo(minors))))),
                PAGE,
                null,
                null,
                new TemplateSigning(RETENTION_MONTHS, true));
    }
}
