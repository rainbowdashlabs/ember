/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
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
            int memberRoleId,
            int memberManagerRoleId,
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
                null,
                null,
                admin.id());
        formRepository.updateStatus(survey.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                survey.id(),
                0,
                FormQuestion.QuestionType.RATING,
                "Wie zufrieden bist du insgesamt?",
                "1 = sehr unzufrieden, 5 = sehr zufrieden",
                true,
                false,
                FormQuestionConfig.parse("{\"scale\":5,\"icon\":\"STAR\"}"));
        formRepository.createQuestion(
                survey.id(),
                1,
                FormQuestion.QuestionType.CHOICE,
                "Was gefällt dir am besten?",
                "",
                false,
                true,
                FormQuestionConfig.parse(
                        "{\"multiSelect\":true,\"dropdown\":false,\"allowOther\":true,\"options\":[\"Übungen\",\"Gemeinschaft\",\"Ausflüge\",\"Wettbewerbe\"],\"multiLimitType\":\"NONE\"}"));
        formRepository.createQuestion(
                survey.id(),
                2,
                FormQuestion.QuestionType.TEXT,
                "Hast du Verbesserungsvorschläge?",
                "",
                false,
                false,
                FormQuestionConfig.parse("{\"longAnswer\":true}"));

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
            formRepository.upsertAnswer(response.id(), surveyQuestions.get(0).id(), "{\"rating\":" + rating + "}");
            int[] selected = rng.nextInt(2) == 0 ? new int[] {0, 2} : new int[] {1, 3};
            formRepository.upsertAnswer(
                    response.id(),
                    surveyQuestions.get(1).id(),
                    "{\"selected\":[" + selected[0] + "," + selected[1] + "],\"other\":\"\"");
            formRepository.upsertAnswer(
                    response.id(),
                    surveyQuestions.get(2).id(),
                    "{\"text\":\"" + suggestions[i % suggestions.length] + "\"");
        }

        // Add remaining types to survey: DATE, RANKING, LIKERT
        formRepository.createQuestion(
                survey.id(),
                3,
                FormQuestion.QuestionType.DATE,
                "Wann bist du der Jugendfeuerwehr beigetreten?",
                "",
                false,
                false,
                FormQuestionConfig.parse("{}"));
        formRepository.createQuestion(
                survey.id(),
                4,
                FormQuestion.QuestionType.RANKING,
                "Ordne die Aktivitäten nach Beliebtheit",
                "",
                false,
                true,
                FormQuestionConfig.parse("{\"options\":[\"Übungen\",\"Wettbewerbe\",\"Ausflüge\",\"Theorie\"]}"));
        formRepository.createQuestion(
                survey.id(),
                5,
                FormQuestion.QuestionType.LIKERT,
                "Wie bewertest du die folgenden Bereiche?",
                "",
                false,
                false,
                FormQuestionConfig.parse(
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
                    "{\"date\":\"202" + (2 + rng.nextInt(4)) + "-0" + (1 + rng.nextInt(9)) + "-15\"");
            int[] rankOrder = {rng.nextInt(4), (1 + rng.nextInt(3)) % 4, (2 + rng.nextInt(2)) % 4, 3 - rng.nextInt(2)};
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(4).id(),
                    "{\"order\":[" + rankOrder[0] + "," + rankOrder[1] + "," + rankOrder[2] + "," + rankOrder[3]
                            + "]}");
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(5).id(),
                    "{\"ratings\":{\"0\":" + (3 + rng.nextInt(3)) + ",\"1\":" + (3 + rng.nextInt(3)) + ",\"2\":"
                            + (2 + rng.nextInt(4)) + "}}");
        }

        // Form 2: CLOSED comprehensive form with ALL types + responses
        var feedback = formRepository.create(
                stationId,
                "Feedback Übungsabend",
                "Rückmeldung zum letzten Übungsabend",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(feedback.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                feedback.id(),
                0,
                FormQuestion.QuestionType.CHOICE,
                "Würdest du wieder teilnehmen?",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja\",\"Vielleicht\",\"Nein\"],\"multiLimitType\":\"NONE\"}"));
        formRepository.createQuestion(
                feedback.id(),
                1,
                FormQuestion.QuestionType.TEXT,
                "Was hat dir besonders gefallen?",
                "",
                false,
                false,
                FormQuestionConfig.parse("{\"longAnswer\":true}"));
        formRepository.createQuestion(
                feedback.id(),
                2,
                FormQuestion.QuestionType.RATING,
                "Gesamtbewertung",
                "1 = schlecht, 10 = super",
                true,
                false,
                FormQuestionConfig.parse("{\"scale\":10,\"icon\":\"HEART\"}"));
        formRepository.createQuestion(
                feedback.id(),
                3,
                FormQuestion.QuestionType.DATE,
                "An welchem Datum warst du dabei?",
                "",
                false,
                false,
                FormQuestionConfig.parse("{}"));
        formRepository.createQuestion(
                feedback.id(),
                4,
                FormQuestion.QuestionType.RANKING,
                "Was war am wichtigsten?",
                "",
                false,
                true,
                FormQuestionConfig.parse("{\"options\":[\"Teamwork\",\"Technik\",\"Fitness\",\"Spaß\"]}"));
        formRepository.createQuestion(
                feedback.id(),
                5,
                FormQuestion.QuestionType.LIKERT,
                "Bewerte die folgenden Aspekte",
                "",
                true,
                false,
                FormQuestionConfig.parse(
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
                    response.id(), feedbackQuestions.get(0).id(), "{\"selected\":[" + choiceIdx + "],\"other\":\"\"}");
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(1).id(),
                    "{\"text\":\"" + feedbackTexts[i % feedbackTexts.length] + "\"");
            formRepository.upsertAnswer(
                    response.id(), feedbackQuestions.get(2).id(), "{\"rating\":" + (5 + rng.nextInt(6)) + "}");
            formRepository.upsertAnswer(response.id(), feedbackQuestions.get(3).id(), "{\"date\":\"2026-05-10\"}");
            int[] order = {rng.nextInt(4), (1 + rng.nextInt(3)) % 4, 2, 3};
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(4).id(),
                    "{\"order\":[" + order[0] + "," + order[1] + "," + order[2] + "," + order[3] + "]");
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(5).id(),
                    "{\"ratings\":{\"0\":" + (3 + rng.nextInt(3)) + ",\"1\":" + (2 + rng.nextInt(4)) + ",\"2\":"
                            + (4 + rng.nextInt(2)) + ",\"3\":" + (2 + rng.nextInt(3)) + "}}");
        }
        formRepository.updateStatus(feedback.id(), Form.FormStatus.CLOSED);

        // Form 3: Member-only form (restricted to MEMBER role)
        var memberOnly = formRepository.create(
                stationId,
                "Persönliche Einschätzung",
                "Nur für Mitglieder — Verwalter können dieses Formular für ihre verwalteten Mitglieder ausfüllen.",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(memberOnly.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                memberOnly.id(),
                0,
                FormQuestion.QuestionType.RATING,
                "Wie wohl fühlst du dich in der Gruppe?",
                "1 = gar nicht, 5 = sehr wohl",
                true,
                false,
                FormQuestionConfig.parse("{\"scale\":5,\"icon\":\"STAR\"}"));
        formRepository.createQuestion(
                memberOnly.id(),
                1,
                FormQuestion.QuestionType.TEXT,
                "Was wünschst du dir für die nächsten Monate?",
                "",
                false,
                false,
                FormQuestionConfig.parse("{\"longAnswer\":true}"));
        formRepository.createQuestion(
                memberOnly.id(),
                2,
                FormQuestion.QuestionType.CHOICE,
                "Möchtest du an einem Wettbewerb teilnehmen?",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja, unbedingt!\",\"Vielleicht\",\"Nein, lieber nicht\"],\"multiLimitType\":\"NONE\"}"));
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                memberOnly.id(),
                List.of(memberRoleId),
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
                null,
                null,
                admin.id());
        formRepository.updateStatus(bothRoles.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                bothRoles.id(),
                0,
                FormQuestion.QuestionType.DATE,
                "An welchem Wochenende passt es dir am besten?",
                "",
                true,
                false,
                FormQuestionConfig.parse("{}"));
        formRepository.createQuestion(
                bothRoles.id(),
                1,
                FormQuestion.QuestionType.CHOICE,
                "Kannst du beim Aufbau helfen?",
                "",
                false,
                false,
                FormQuestionConfig.parse(
                        "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja\",\"Nein\",\"Vielleicht\"],\"multiLimitType\":\"NONE\"}"));
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                bothRoles.id(),
                List.of(memberRoleId, memberManagerRoleId),
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
                null,
                null,
                admin.id());
        formRepository.updateStatus(wettkampfForm.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                wettkampfForm.id(),
                0,
                FormQuestion.QuestionType.RATING,
                "Wie fit fühlst du dich für den Wettkampf?",
                "1 = gar nicht, 5 = top vorbereitet",
                true,
                false,
                FormQuestionConfig.parse("{\"scale\":5,\"icon\":\"STAR\"}"));
        formRepository.createQuestion(
                wettkampfForm.id(),
                1,
                FormQuestion.QuestionType.CHOICE,
                "Welche Disziplin möchtest du übernehmen?",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        "{\"multiSelect\":true,\"dropdown\":false,\"allowOther\":true,\"options\":[\"Löschangriff\",\"Staffellauf\",\"Knotenkunde\",\"Erste Hilfe\"],\"multiLimitType\":\"AT_MOST\",\"multiLimit\":2}"));
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
                null,
                null,
                admin.id());
        formRepository.updateStatus(anfaengerForm.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                anfaengerForm.id(),
                0,
                FormQuestion.QuestionType.LIKERT,
                "Bewerte deine bisherige Erfahrung",
                "",
                true,
                false,
                FormQuestionConfig.parse(
                        "{\"statements\":[\"Ich verstehe die Übungen\",\"Ich fühle mich willkommen\",\"Ich lerne viel Neues\"],\"scaleMin\":1,\"scaleMax\":5,\"scaleLabels\":[]}"));
        formRepository.createQuestion(
                anfaengerForm.id(),
                1,
                FormQuestion.QuestionType.TEXT,
                "Was können wir für dich verbessern?",
                "",
                false,
                false,
                FormQuestionConfig.parse("{\"longAnswer\":true}"));
        restrictionRepository.setRestrictions(
                RestrictionType.FORM.table(),
                RestrictionType.FORM.fkColumn(),
                anfaengerForm.id(),
                List.of(),
                List.of(anfaengerGroupId),
                List.of(),
                List.of());

        log.info(
                "Demo: Created 6 forms (open all types, closed all types, member-only, member+manager, tag-restricted, group-restricted)");
    }
}
