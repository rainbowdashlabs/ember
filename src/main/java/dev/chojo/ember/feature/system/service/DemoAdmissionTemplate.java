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
 * The admission application the demo's procedure "Neues Mitglied aufnehmen" asks a new member to sign: a
 * letter in German saying the member joins the youth fire brigade, what that brings with it and how it ends.
 *
 * <p>The member signs it, and so does every guardian of a young member, whose lines and the sentence about
 * them are printed only for the members of the youth fire brigade. The youth warden takes the application
 * in for the station and signs as its issuer. Signed copies are kept for four years after the member has
 * left and go out to the signers with the sealed PDF attached.
 *
 * <p>It is legal, not meant for appointments, and not offered for self service, since a procedure asks for
 * it when a member is taken in.
 */
final class DemoAdmissionTemplate {

    /** What the application is called in the list of templates and on the procedure step. */
    static final String NAME = "Aufnahmeantrag Jugendfeuerwehr";

    /** How long signed applications are kept after the member has left: four years. */
    static final int RETENTION_MONTHS = 48;

    private static final String TITLE = "**Antrag auf Aufnahme in die Jugendfeuerwehr**";

    private static final String APPLICANT = """
            Name: {{member.fullName}}\\
            Geburtsdatum: {{member.birthDate}}""";

    private static final String APPLICATION = """
            Hiermit wird die Aufnahme in die Jugendfeuerwehr der {{station.name}} beantragt. Die \
            Jugendfeuerwehr übt einmal in der Woche, fährt zu Zeltlagern und Wettbewerben und bereitet auf den \
            Dienst in der Einsatzabteilung vor.""";

    private static final String DUTIES = """
            Mitglieder nehmen regelmäßig an den Übungsabenden teil, tragen die ausgegebene Kleidung und halten \
            sich an die Hinweise der Betreuerinnen und Betreuer. Kleidung und Ausrüstung bleiben Eigentum der \
            Feuerwehr und werden beim Austritt zurückgegeben.""";

    private static final String ENDING = """
            Die Mitgliedschaft endet mit einer formlosen Erklärung bei {{issuer.fullName}} \
            ({{issuer.function}}) oder mit dem Wechsel in die Einsatzabteilung.""";

    private static final String ONE_GUARDIAN = """
            Für Minderjährige stimmt zusätzlich die erziehungsberechtigte Person zu: \
            {{guardian1.fullName}}.""";

    private static final String TWO_GUARDIANS = """
            Für Minderjährige stimmen zusätzlich beide erziehungsberechtigten Personen zu: \
            {{guardian1.fullName}} und {{guardian2.fullName}}.""";

    private static final String PLACE_AND_DATE = "Musterstadt, den {{today}}";

    private static final String PARTICIPANT = "{{member.fullName}}";

    private static final String PARTICIPANT_STATEMENT = "Ich beantrage die Aufnahme in die Jugendfeuerwehr.";

    private static final String GUARDIAN = "Erziehungsberechtigte Person";

    private static final String GUARDIAN_STATEMENT =
            "Als erziehungsberechtigte Person stimme ich der Aufnahme meines Kindes in die Jugendfeuerwehr zu.";

    private static final String ISSUER = """
            {{issuer.fullName}}\\
            {{issuer.function}} der {{station.name}}""";

    private static final String ISSUER_STATEMENT = "Ich nehme den Antrag für die Jugendfeuerwehr entgegen.";

    private DemoAdmissionTemplate() {}

    /**
     * The application as the editor would send it.
     *
     * @param warden the youth warden of the station, who issues the application and takes it in
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
                false,
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
                        List.of(cell(100, text(APPLICANT))),
                        List.of(cell(100, text(APPLICATION))),
                        List.of(cell(100, text(DUTIES))),
                        List.of(cell(100, text(ENDING))),
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
