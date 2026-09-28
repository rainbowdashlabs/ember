/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig.Option;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The migration that gives every option a key rewrites configs and answers stored the old way,
 * position by position, and leaves anything already in the new shape as it is.
 *
 * <p>The database of this test is already migrated, so the old shape is written by hand and the patch
 * run over it once more, which is exactly what it met on a database that had answers.
 */
class FormOptionKeyMigrationTest extends RepositoryTestBase {
    private static final String PATCH = "database/postgresql/1/patch_75.sql";

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int formId;
    private static int responseId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Option Key Migration");
        account = accountRepo.create("option-key-migration@test.com", "Option", "Keys");
        member = stationMemberRepo.create(station.id(), account.id());
        formId = formRepo.create(
                        station.id(), "Old form", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL)
                .id();
        responseId = formRepo.createResponse(formId, member.id(), member.id()).id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aChoiceGetsKeyedOptionsAndItsSelectionsBecomeKeys() throws Exception {
        int question = oldQuestion(FormQuestionType.CHOICE, """
                {"options":["Ja","Nein","Vielleicht"],"multiSelect":true,"allowOther":true}""");
        oldAnswer(question, """
                {"type":"CHOICE","selected":[2,0,9],"other":"Später"}""");

        migrate();

        var migrated = question(question);
        assertEquals(
                Option.numbered("Ja", "Nein", "Vielleicht"), migrated.config().keyedOptions());
        assertEquals(
                new FormAnswerValue.Choice(List.of("o2", "o0"), "Später"),
                answer(migrated),
                "the order is kept and a position outside the options is dropped");
    }

    @Test
    void aRankingOrderBecomesAnOrderOfKeys() throws Exception {
        int question = oldQuestion(FormQuestionType.RANKING, """
                {"options":["A","B","C"]}""");
        oldAnswer(question, """
                {"type":"RANKING","order":[1,2,0]}""");

        migrate();

        var migrated = question(question);
        assertEquals(Option.numbered("A", "B", "C"), migrated.config().keyedOptions());
        assertEquals(new FormAnswerValue.Ranking(List.of("o1", "o2", "o0")), answer(migrated));
    }

    @Test
    void likertRatingsAreKeyedByStatementKey() throws Exception {
        int question = oldQuestion(FormQuestionType.LIKERT, """
                {"statements":["Essen","Zelte"],"scaleMin":1,"scaleMax":5,"scaleLabels":["schlecht","","","","gut"]}""");
        oldAnswer(question, """
                {"type":"LIKERT","ratings":{"0":4,"1":2,"7":5}}""");

        migrate();

        var migrated = question(question);
        var likert = (FormQuestionConfig.Likert) migrated.config();
        assertEquals(Option.numbered("Essen", "Zelte"), likert.statements());
        assertEquals(List.of("schlecht", "", "", "", "gut"), likert.scaleLabels(), "the scale labels stay text");
        assertEquals(new FormAnswerValue.Likert(Map.of("o0", 4, "o1", 2)), answer(migrated));
    }

    @Test
    void anEmptyAnswerAndOtherQuestionTypesStayAsTheyAre() throws Exception {
        int choice = oldQuestion(FormQuestionType.CHOICE, """
                {"options":["Ja"],"allowOther":true}""");
        oldAnswer(choice, """
                {"type":"CHOICE","selected":[],"other":"nur Text"}""");
        int text = oldQuestion(FormQuestionType.TEXT, """
                {"longAnswer":true}""");
        oldAnswer(text, """
                {"type":"TEXT","text":"0"}""");

        migrate();

        assertEquals(new FormAnswerValue.Choice(List.of(), "nur Text"), answer(question(choice)));
        assertEquals(new FormQuestionConfig.Text(true), question(text).config());
        assertEquals(new FormAnswerValue.Text("0"), answer(question(text)));
    }

    @Test
    void runningItAgainChangesNothing() throws Exception {
        int question = oldQuestion(FormQuestionType.CHOICE, """
                {"options":["Ja","Nein"]}""");
        oldAnswer(question, """
                {"type":"CHOICE","selected":[1]}""");

        migrate();
        var once = question(question);
        var onceAnswer = answer(once);
        migrate();

        assertEquals(once.config(), question(question).config());
        assertEquals(onceAnswer, answer(question(question)));
    }

    private static int oldQuestion(FormQuestionType type, String config) {
        return query("""
                INSERT INTO form_question(form_id, page_id, position, question_type, title, config)
                VALUES (:form_id, (SELECT id FROM form_page WHERE form_id = :form_id), 0, :type, 'Frage', :config::JSONB)
                RETURNING id;""")
                .single(call().bind("form_id", formId).bind("type", type.name()).bind("config", config))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    private static void oldAnswer(int questionId, String value) {
        query("""
                INSERT INTO form_answer(response_id, question_id, value)
                VALUES (:response_id, :question_id, :value::JSONB);""")
                .single(call().bind("response_id", responseId)
                        .bind("question_id", questionId)
                        .bind("value", value))
                .insert();
    }

    private static FormQuestion question(int questionId) {
        return formRepo.findQuestions(formId).stream()
                .filter(question -> question.id() == questionId)
                .findFirst()
                .orElseThrow();
    }

    private static FormAnswerValue answer(FormQuestion question) {
        return FormAnswerValue.parse(
                question.formQuestionType(),
                formRepo.findAnswersToQuestion(question.id()).getFirst().value());
    }

    private static void migrate() throws IOException, SQLException {
        String patch;
        try (var in = Objects.requireNonNull(
                FormOptionKeyMigrationTest.class.getClassLoader().getResourceAsStream(PATCH))) {
            patch = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(patch.replace("ember_schema", schemaName));
        }
    }
}
