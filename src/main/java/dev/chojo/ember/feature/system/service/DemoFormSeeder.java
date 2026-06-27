/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.restriction.RestrictionType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Seeder for demo form data including surveys, feedback forms, and restricted forms.
 */
@Singleton
public class DemoFormSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoFormSeeder.class);

    private final FormRepository formRepository;
    private final RestrictionRepository restrictionRepository;

    @Inject
    public DemoFormSeeder(FormRepository formRepository, RestrictionRepository restrictionRepository) {
        this.formRepository = formRepository;
        this.restrictionRepository = restrictionRepository;
    }

    public void seedForms(
            int stationId,
            StationMember admin,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            StationUserType memberUserType,
            StationUserType guardianUserType,
            int anfaengerGroupId,
            int wettkampfTagId,
            Random rng) {
        // Form 1: Satisfaction survey (OPEN, with responses)
        var survey = formRepository.create(
                stationId,
                "Zufriedenheitsumfrage",
                "Wie gefällt dir unsere Jugendfeuerwehr?",
                false,
                true,
                false,
                null,
                null,
                admin.id(),
                FormPurpose.INTERNAL);
        formRepository.updateStatus(survey.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                survey.id(),
                0,
                FormQuestionType.RATING,
                "Wie zufrieden bist du insgesamt?",
                "1 = sehr unzufrieden, 5 = sehr zufrieden",
                true,
                false,
                new FormQuestionConfig.Rating(5, FormQuestionConfig.Rating.RatingIcon.STAR));
        formRepository.createQuestion(
                survey.id(),
                1,
                FormQuestionType.CHOICE,
                "Was gefällt dir am besten?",
                "",
                false,
                true,
                new FormQuestionConfig.Choice(
                        List.of("Übungen", "Gemeinschaft", "Ausflüge", "Wettbewerbe"),
                        true,
                        false,
                        true,
                        FormQuestionConfig.MultiLimitType.NONE,
                        null));
        formRepository.createQuestion(
                survey.id(),
                2,
                FormQuestionType.TEXT,
                "Hast du Verbesserungsvorschläge?",
                "",
                false,
                false,
                new FormQuestionConfig.Text(true));

        // Add some responses
        var surveyQuestions = formRepository.findQuestions(survey.id());
        var respondents = new ArrayList<StationMember>();
        respondents.addAll(anfaenger.subList(0, Math.min(5, anfaenger.size())));
        respondents.addAll(fortgeschritten.subList(0, Math.min(4, fortgeschritten.size())));
        String[] suggestions = {"Mehr Ausflüge!", "Öfter draußen üben", "Alles super!", "", "Neue Geräte wären toll"};
        for (int i = 0; i < respondents.size(); i++) {
            var member = respondents.get(i);
            var response = formRepository.createResponse(survey.id(), member.id(), member.id());
            int rating = 3 + rng.nextInt(3);
            formRepository.upsertAnswer(response.id(), surveyQuestions.get(0).id(), new FormAnswerValue.Rating(rating));
            int[] selected = rng.nextInt(2) == 0 ? new int[] {0, 2} : new int[] {1, 3};
            formRepository.upsertAnswer(
                    response.id(),
                    surveyQuestions.get(1).id(),
                    new FormAnswerValue.Choice(List.of(selected[0], selected[1]), ""));
            formRepository.upsertAnswer(
                    response.id(),
                    surveyQuestions.get(2).id(),
                    new FormAnswerValue.Text(suggestions[i % suggestions.length]));
        }

        // Add remaining types to survey: DATE, RANKING, LIKERT
        formRepository.createQuestion(
                survey.id(),
                3,
                FormQuestionType.DATE,
                "Wann bist du der Jugendfeuerwehr beigetreten?",
                "",
                false,
                false,
                new FormQuestionConfig.Unknown());
        formRepository.createQuestion(
                survey.id(),
                4,
                FormQuestionType.RANKING,
                "Ordne die Aktivitäten nach Beliebtheit",
                "",
                false,
                true,
                new FormQuestionConfig.Ranking(List.of("Übungen", "Wettbewerbe", "Ausflüge", "Theorie")));
        formRepository.createQuestion(
                survey.id(),
                5,
                FormQuestionType.LIKERT,
                "Wie bewertest du die folgenden Bereiche?",
                "",
                false,
                false,
                FormQuestionConfig.parse(
                        FormQuestionType.LIKERT,
                        "{\"statements\":[\"Ausrüstung\",\"Betreuung\",\"Abwechslung\"],\"scaleMin\":1,\"scaleMax\":5,\"scaleLabels\":[]}"));

        // Re-fetch questions after adding more
        surveyQuestions = formRepository.findQuestions(survey.id());
        // Add responses for the new question types
        for (StationMember member : respondents) {
            var existingResponse =
                    formRepository.findResponse(survey.id(), member.id()).orElseThrow();
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(3).id(),
                    new FormAnswerValue.DateValue("202" + (2 + rng.nextInt(4)) + "-0" + (1 + rng.nextInt(9)) + "-15"));
            int[] rankOrder = {rng.nextInt(4), (1 + rng.nextInt(3)) % 4, (2 + rng.nextInt(2)) % 4, 3 - rng.nextInt(2)};
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(4).id(),
                    new FormAnswerValue.Ranking(List.of(rankOrder[0], rankOrder[1], rankOrder[2], rankOrder[3])));
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(5).id(),
                    new FormAnswerValue.Likert(
                            Map.of("0", 3 + rng.nextInt(3), "1", 3 + rng.nextInt(3), "2", 2 + rng.nextInt(4))));
        }

        // Form 2: CLOSED comprehensive form with ALL types + responses
        var feedback = formRepository.create(
                stationId,
                "Feedback Übungsabend",
                "Rückmeldung zum letzten Übungsabend",
                false,
                true,
                false,
                null,
                null,
                admin.id(),
                FormPurpose.INTERNAL);
        formRepository.updateStatus(feedback.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                feedback.id(),
                0,
                FormQuestionType.CHOICE,
                "Würdest du wieder teilnehmen?",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        FormQuestionType.CHOICE,
                        "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja\",\"Vielleicht\",\"Nein\"],\"multiLimitType\":\"NONE\"}"));
        formRepository.createQuestion(
                feedback.id(),
                1,
                FormQuestionType.TEXT,
                "Was hat dir besonders gefallen?",
                "",
                false,
                false,
                new FormQuestionConfig.Text(true));
        formRepository.createQuestion(
                feedback.id(),
                2,
                FormQuestionType.RATING,
                "Gesamtbewertung",
                "1 = schlecht, 10 = super",
                true,
                false,
                new FormQuestionConfig.Rating(10, FormQuestionConfig.Rating.RatingIcon.HEART));
        formRepository.createQuestion(
                feedback.id(),
                3,
                FormQuestionType.DATE,
                "An welchem Datum warst du dabei?",
                "",
                false,
                false,
                new FormQuestionConfig.Unknown());
        formRepository.createQuestion(
                feedback.id(),
                4,
                FormQuestionType.RANKING,
                "Was war am wichtigsten?",
                "",
                false,
                true,
                new FormQuestionConfig.Ranking(List.of("Teamwork", "Technik", "Fitness", "Spaß")));
        formRepository.createQuestion(
                feedback.id(),
                5,
                FormQuestionType.LIKERT,
                "Bewerte die folgenden Aspekte",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        FormQuestionType.LIKERT,
                        "{\"statements\":[\"Organisation\",\"Lerninhalte\",\"Spaßfaktor\",\"Zeitdauer\"],\"scaleMin\":1,\"scaleMax\":5,\"scaleLabels\":[]}"));

        var feedbackQuestions = formRepository.findQuestions(feedback.id());
        String[] feedbackTexts = {
            "Tolle Übung!", "Mehr davon!", "War ok", "Super organisiert", "Könnte besser sein", "Hat Spaß gemacht"
        };
        for (int i = 0; i < Math.min(8, anfaenger.size()); i++) {
            var member = anfaenger.get(i);
            var response = formRepository.createResponse(feedback.id(), member.id(), member.id());
            int choiceIdx = rng.nextInt(3);
            formRepository.upsertAnswer(
                    response.id(), feedbackQuestions.get(0).id(), new FormAnswerValue.Choice(List.of(choiceIdx), ""));
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(1).id(),
                    new FormAnswerValue.Text(feedbackTexts[i % feedbackTexts.length]));
            formRepository.upsertAnswer(
                    response.id(), feedbackQuestions.get(2).id(), new FormAnswerValue.Rating(5 + rng.nextInt(6)));
            formRepository.upsertAnswer(
                    response.id(), feedbackQuestions.get(3).id(), new FormAnswerValue.DateValue("2026-05-10"));
            int[] order = {rng.nextInt(4), (1 + rng.nextInt(3)) % 4, 2, 3};
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(4).id(),
                    new FormAnswerValue.Ranking(List.of(order[0], order[1], order[2], order[3])));
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(5).id(),
                    new FormAnswerValue.Likert(Map.of(
                            "0",
                            3 + rng.nextInt(3),
                            "1",
                            2 + rng.nextInt(4),
                            "2",
                            4 + rng.nextInt(2),
                            "3",
                            2 + rng.nextInt(3))));
        }
        formRepository.updateStatus(feedback.id(), Form.FormStatus.CLOSED);

        // Form 3: Member-only form (restricted to MEMBER role)
        var memberOnly = formRepository.create(
                stationId,
                "Persönliche Einschätzung",
                "Nur für Mitglieder — Verwalter können dieses Formular für ihre verwalteten Mitglieder ausfüllen.",
                false,
                true,
                false,
                null,
                null,
                admin.id(),
                FormPurpose.INTERNAL);
        formRepository.updateStatus(memberOnly.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                memberOnly.id(),
                0,
                FormQuestionType.RATING,
                "Wie wohl fühlst du dich in der Gruppe?",
                "1 = gar nicht, 5 = sehr wohl",
                true,
                false,
                new FormQuestionConfig.Rating(5, FormQuestionConfig.Rating.RatingIcon.STAR));
        formRepository.createQuestion(
                memberOnly.id(),
                1,
                FormQuestionType.TEXT,
                "Was wünschst du dir für die nächsten Monate?",
                "",
                false,
                false,
                new FormQuestionConfig.Text(true));
        formRepository.createQuestion(
                memberOnly.id(),
                2,
                FormQuestionType.CHOICE,
                "Möchtest du an einem Wettbewerb teilnehmen?",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        FormQuestionType.CHOICE,
                        "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja, unbedingt!\",\"Vielleicht\",\"Nein, lieber nicht\"],\"multiLimitType\":\"NONE\"}"));
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                memberOnly.id(),
                List.of(memberUserType),
                List.of(),
                List.of(),
                List.of());

        // Form 4: For MEMBER + GUARDIAN (both can fill for themselves)
        var bothRoles = formRepository.create(
                stationId,
                "Terminplanung Herbstfest",
                "Für Mitglieder und Verwalter — bitte gebt eure Verfügbarkeit an.",
                false,
                true,
                false,
                null,
                null,
                admin.id(),
                FormPurpose.INTERNAL);
        formRepository.updateStatus(bothRoles.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                bothRoles.id(),
                0,
                FormQuestionType.DATE,
                "An welchem Wochenende passt es dir am besten?",
                "",
                true,
                false,
                new FormQuestionConfig.Unknown());
        formRepository.createQuestion(
                bothRoles.id(),
                1,
                FormQuestionType.CHOICE,
                "Kannst du beim Aufbau helfen?",
                "",
                false,
                false,
                FormQuestionConfig.parse(
                        FormQuestionType.CHOICE,
                        "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja\",\"Nein\",\"Vielleicht\"],\"multiLimitType\":\"NONE\"}"));
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                bothRoles.id(),
                List.of(memberUserType, guardianUserType),
                List.of(),
                List.of(),
                List.of());

        // Form 5: Restricted to Wettkampfgruppe tag only
        var wettkampfForm = formRepository.create(
                stationId,
                "Wettkampf-Vorbereitung",
                "Nur für Mitglieder der Wettkampfgruppe.",
                false,
                true,
                false,
                null,
                null,
                admin.id(),
                FormPurpose.INTERNAL);
        formRepository.updateStatus(wettkampfForm.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                wettkampfForm.id(),
                0,
                FormQuestionType.RATING,
                "Wie fit fühlst du dich für den Wettkampf?",
                "1 = gar nicht, 5 = top vorbereitet",
                true,
                false,
                new FormQuestionConfig.Rating(5, FormQuestionConfig.Rating.RatingIcon.STAR));
        formRepository.createQuestion(
                wettkampfForm.id(),
                1,
                FormQuestionType.CHOICE,
                "Welche Disziplin möchtest du übernehmen?",
                "",
                true,
                false,
                new FormQuestionConfig.Choice(
                        List.of("Löschangriff", "Staffellauf", "Knotenkunde", "Erste Hilfe"),
                        true,
                        false,
                        true,
                        FormQuestionConfig.MultiLimitType.AT_MOST,
                        2));
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                wettkampfForm.id(),
                List.of(),
                List.of(),
                List.of(wettkampfTagId),
                List.of());

        // Form 6: Restricted to Anfänger group only
        var anfaengerForm = formRepository.create(
                stationId,
                "Anfänger-Feedback",
                "Nur für die Anfänger-Gruppe — wie läuft es bei euch?",
                false,
                true,
                false,
                null,
                null,
                admin.id(),
                FormPurpose.INTERNAL);
        formRepository.updateStatus(anfaengerForm.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                anfaengerForm.id(),
                0,
                FormQuestionType.LIKERT,
                "Bewerte deine bisherige Erfahrung",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        FormQuestionType.LIKERT,
                        "{\"statements\":[\"Ich verstehe die Übungen\",\"Ich fühle mich willkommen\",\"Ich lerne viel Neues\"],\"scaleMin\":1,\"scaleMax\":5,\"scaleLabels\":[]}"));
        formRepository.createQuestion(
                anfaengerForm.id(),
                1,
                FormQuestionType.TEXT,
                "Was können wir für dich verbessern?",
                "",
                false,
                false,
                new FormQuestionConfig.Text(true));
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                anfaengerForm.id(),
                List.of(),
                List.of(anfaengerGroupId),
                List.of(),
                List.of());

        // Form 7: Showcase — one question per type, open, unrestricted
        var showcase = formRepository.create(
                stationId,
                "Showcase: Alle Fragetypen",
                "Dieses Formular zeigt alle verfügbaren Fragetypen. Probiere sie aus!",
                false,
                true,
                false,
                null,
                null,
                admin.id(),
                FormPurpose.INTERNAL);
        formRepository.updateStatus(showcase.id(), Form.FormStatus.OPEN);

        // 1. CHOICE — single select
        formRepository.createQuestion(
                showcase.id(),
                0,
                FormQuestionType.CHOICE,
                "Welche Farbe gefällt dir am besten?",
                "Wähle genau eine Farbe aus.",
                true,
                false,
                new FormQuestionConfig.Choice(
                        List.of("Rot", "Blau", "Grün", "Gelb", "Lila"),
                        false,
                        false,
                        false,
                        FormQuestionConfig.MultiLimitType.NONE,
                        null));

        // 2. CHOICE — multi select with dropdown and "other"
        formRepository.createQuestion(
                showcase.id(),
                1,
                FormQuestionType.CHOICE,
                "Welche Hobbys hast du?",
                "Wähle bis zu 3 Hobbys. Du kannst auch ein eigenes angeben.",
                false,
                true,
                new FormQuestionConfig.Choice(
                        List.of("Sport", "Musik", "Lesen", "Gaming", "Kochen", "Basteln"),
                        true,
                        false,
                        true,
                        FormQuestionConfig.MultiLimitType.AT_MOST,
                        3));

        // 3. TEXT — short answer
        formRepository.createQuestion(
                showcase.id(),
                2,
                FormQuestionType.TEXT,
                "Wie heißt dein Lieblingstier?",
                "Kurze Antwort genügt.",
                false,
                false,
                new FormQuestionConfig.Text(false));

        // 4. TEXT — long answer
        formRepository.createQuestion(
                showcase.id(),
                3,
                FormQuestionType.TEXT,
                "Beschreibe deinen perfekten Tag",
                "Hier hast du viel Platz zum Schreiben.",
                false,
                false,
                new FormQuestionConfig.Text(true));

        // 5. RATING — 5 stars
        formRepository.createQuestion(
                showcase.id(),
                4,
                FormQuestionType.RATING,
                "Wie findest du dieses Formular?",
                "1 = langweilig, 5 = super",
                true,
                false,
                new FormQuestionConfig.Rating(5, FormQuestionConfig.Rating.RatingIcon.STAR));

        // 6. RATING — 10 hearts
        formRepository.createQuestion(
                showcase.id(),
                5,
                FormQuestionType.RATING,
                "Wie sehr magst du Ember?",
                "1–10 Herzen",
                false,
                false,
                new FormQuestionConfig.Rating(10, FormQuestionConfig.Rating.RatingIcon.HEART));

        // 7. DATE
        formRepository.createQuestion(
                showcase.id(),
                6,
                FormQuestionType.DATE,
                "Wann hast du Geburtstag?",
                "",
                false,
                false,
                new FormQuestionConfig.Date());

        // 8. RANKING
        formRepository.createQuestion(
                showcase.id(),
                7,
                FormQuestionType.RANKING,
                "Ordne die Jahreszeiten nach deiner Vorliebe",
                "Ziehe die Einträge in die richtige Reihenfolge.",
                true,
                true,
                new FormQuestionConfig.Ranking(List.of("Frühling", "Sommer", "Herbst", "Winter")));

        // 9. LIKERT
        formRepository.createQuestion(
                showcase.id(),
                8,
                FormQuestionType.LIKERT,
                "Wie stimmst du folgenden Aussagen zu?",
                "1 = stimme gar nicht zu, 5 = stimme voll zu",
                true,
                false,
                new FormQuestionConfig.Likert(
                        List.of("Ich bin gerne draußen", "Ich arbeite gerne im Team", "Ich probiere gerne Neues aus"),
                        1,
                        5,
                        List.of("stimme nicht zu", "", "", "", "stimme zu")));

        log.info(
                "Demo: Created 7 forms (open all types, closed all types, member-only, member+manager, tag-restricted, group-restricted, showcase)");
    }
}
