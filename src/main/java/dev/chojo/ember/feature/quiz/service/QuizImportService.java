/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CatalogTransfer.CategoryEntry;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer.QuestionEntry;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.util.CsvParser;
import dev.chojo.ember.util.Json;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns an uploaded sheet into a draft of the catalog file the import reads.
 *
 * <p>Nothing is written here. The sheet is read into the same shape a catalog file carries, the
 * wizard shows that draft and lets it be corrected, and what the person confirms is what the
 * import creates. Reading a cell into a typed config therefore happens once, on the way in, rather
 * than once in the wizard and again behind it.
 */
@Singleton
public class QuizImportService {
    private static final Logger log = LoggerFactory.getLogger(QuizImportService.class);
    private static final String DEFAULT_SEPARATOR = ",";
    private static final String DEFAULT_ANSWER_SEPARATOR = ";";
    private static final double DEFAULT_POINTS = 1;
    private static final double MULTIPLE_CHOICE_POINTS_PER_CORRECT = 0.5;
    private static final int FREE_ANSWER_LINES = 3;
    private static final int ENUMERATION_REQUIRED_COUNT = 3;
    private static final String FALLBACK_CATEGORY_KEY = "category";

    /**
     * Drafts every mapped row of a sheet. Rows without question text are skipped, because a sheet
     * kept by hand tends to end in blank lines that mean nothing.
     *
     * @param csvContent the decoded sheet
     * @param mappings   which column carries which field, plus the parsing separators
     * @throws BadRequestResponse when the sheet cannot be parsed, the question column is missing,
     *                            or a row names a question type Ember does not know
     */
    public CsvDraft draft(String csvContent, CsvMappings mappings) {
        var parsed = parse(csvContent, mappings.separatorChar());
        var columns = ColumnIndex.resolve(parsed.headers(), mappings);
        var categories = new CategoryKeys();
        var questions = new ArrayList<DraftQuestion>();

        var rows = parsed.rows();
        for (int i = 0; i < rows.size(); i++) {
            var cells = rows.get(i);
            String title = columns.cell(columns.question(), cells);
            if (title.isEmpty()) continue;

            var type = resolveType(columns.cell(columns.type(), cells), mappings.defaultType());
            String answer = columns.cell(columns.answer(), cells);
            String separator = mappings.answerSeparatorOrDefault();
            var config =
                    buildConfig(type, answer, columns.cell(columns.distractors(), cells), separator, columns, cells);

            questions.add(new DraftQuestion(
                    new QuestionEntry(
                            categories.keyOf(columns.cell(columns.category(), cells)),
                            type.name(),
                            title,
                            columns.cell(columns.description(), cells),
                            emptyToNull(columns.cell(columns.image(), cells)),
                            resolvePoints(columns.cell(columns.points(), cells)),
                            true,
                            Json.EMPTY_TOLERANT_CONFIG_MAPPER.valueToTree(config),
                            questions.size()),
                    answer,
                    separator));
        }
        log.info("Drafted {} questions from a sheet of {} rows", questions.size(), rows.size());
        return new CsvDraft(categories.entries(), questions);
    }

    private CsvParser.ParsedCsv parse(String csvContent, char separator) {
        try {
            return CsvParser.parse(csvContent, separator);
        } catch (IOException e) {
            log.warn("Failed to parse an uploaded sheet", e);
            throw new BadRequestResponse("Failed to parse CSV");
        }
    }

    private QuizQuestionType resolveType(String cell, QuizQuestionType fallback) {
        if (!cell.isEmpty()) return parseQuestionType(cell);
        return fallback != null ? fallback : QuizQuestionType.MULTIPLE_CHOICE;
    }

    private double resolvePoints(String cell) {
        return parseDouble(cell, DEFAULT_POINTS);
    }

    private static double parseDouble(String cell, double fallback) {
        if (cell.isEmpty()) return fallback;
        try {
            return Double.parseDouble(cell.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String cell, int fallback) {
        if (cell.isEmpty()) return fallback;
        try {
            return Integer.parseInt(cell);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Maps the type spellings the sheets in the field actually use - English and German,
     * spelled out or abbreviated - onto the question types.
     */
    private QuizQuestionType parseQuestionType(String type) {
        return switch (type.toUpperCase().replace(" ", "_").replace("-", "_")) {
            case "MULTIPLE_CHOICE", "MC" -> QuizQuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE", "TF", "WAHR_FALSCH" -> QuizQuestionType.TRUE_FALSE;
            case "FREE_ANSWER", "FREE", "FREITEXT" -> QuizQuestionType.FREE_ANSWER;
            case "FILL_IN_THE_BLANK", "FILL_BLANK", "LUECKENTEXT", "LÜCKENTEXT" -> QuizQuestionType.FILL_IN_THE_BLANK;
            case "CONNECT", "ZUORDNUNG" -> QuizQuestionType.CONNECT;
            case "ORDERING", "REIHENFOLGE" -> QuizQuestionType.ORDERING;
            case "IMAGE_TEXT" -> QuizQuestionType.IMAGE_TEXT;
            case "ENUMERATION", "AUFZÄHLUNG", "AUFZAEHLUNG" -> QuizQuestionType.ENUMERATION;
            default -> throw new BadRequestResponse("Invalid question type: " + type);
        };
    }

    /**
     * Builds the typed config for a question type from the answer cell. The first entry of a
     * multiple-choice cell is the correct option; connect pairs are written as {@code left=right}.
     * A fill-in-the-blank offers the answers together with whatever the distractor column adds,
     * so the member picks from a list rather than facing an empty box.
     */
    private QuestionConfig buildConfig(
            QuizQuestionType type,
            String answer,
            String distractorCell,
            String answerSeparator,
            ColumnIndex columns,
            List<String> cells) {
        var parts = split(answer, answerSeparator);
        var distractors = split(distractorCell, answerSeparator);
        double pointsPerCorrect = parseDouble(columns.cell(columns.pointsPerCorrect(), cells), 0);
        return switch (type) {
            case MULTIPLE_CHOICE -> multipleChoice(parts, distractors, pointsPerCorrect);
            case TRUE_FALSE -> new QuestionConfig.TrueFalse(isAffirmative(answer));
            case FREE_ANSWER -> new QuestionConfig.FreeAnswer(parts, FREE_ANSWER_LINES, pointsPerCorrect);
            case FILL_IN_THE_BLANK ->
                new QuestionConfig.FillInTheBlank("", parts, distractors, !distractors.isEmpty(), pointsPerCorrect);
            case CONNECT -> connect(parts, pointsPerCorrect);
            case ORDERING -> new QuestionConfig.Ordering(parts, pointsPerCorrect);
            case IMAGE_TEXT -> new QuestionConfig.ImageText(null, answer.isEmpty() ? null : answer);
            case ENUMERATION ->
                new QuestionConfig.Enumeration(
                        parts,
                        parseInt(
                                columns.cell(columns.requiredCount(), cells),
                                Math.min(ENUMERATION_REQUIRED_COUNT, parts.size())),
                        isAffirmative(columns.cell(columns.orderedRequired(), cells)),
                        pointsPerCorrect);
        };
    }

    /**
     * Reads a multiple-choice row two ways, because sheets are kept both ways. Where wrong answers
     * have a column of their own, everything in the answer cell is correct and everything in that
     * column is not. Where they do not, the sheet is one that lists the right answer first and the
     * wrong ones after it, which is how these sheets were read before there was a second column.
     */
    private QuestionConfig multipleChoice(List<String> answers, List<String> distractors, double pointsPerCorrect) {
        var options = new ArrayList<QuestionConfig.MultipleChoice.Option>();
        if (distractors.isEmpty()) {
            for (int i = 0; i < answers.size(); i++) {
                options.add(new QuestionConfig.MultipleChoice.Option(answers.get(i), i == 0));
            }
        } else {
            answers.forEach(text -> options.add(new QuestionConfig.MultipleChoice.Option(text, true)));
            distractors.forEach(text -> options.add(new QuestionConfig.MultipleChoice.Option(text, false)));
        }
        return new QuestionConfig.MultipleChoice(
                options, pointsPerCorrect > 0 ? pointsPerCorrect : MULTIPLE_CHOICE_POINTS_PER_CORRECT);
    }

    private QuestionConfig connect(List<String> parts, double pointsPerCorrect) {
        var pairs = parts.stream()
                .map(part -> part.split("=", 2))
                .map(split -> new QuestionConfig.Connect.Pair(split[0].trim(), split.length > 1 ? split[1].trim() : ""))
                .toList();
        return new QuestionConfig.Connect(pairs, pointsPerCorrect);
    }

    private boolean isAffirmative(String answer) {
        return answer.equalsIgnoreCase("true")
                || answer.equals("1")
                || answer.equalsIgnoreCase("wahr")
                || answer.equalsIgnoreCase("ja");
    }

    private List<String> split(String answer, String separator) {
        return Arrays.stream(answer.split(Pattern.quote(separator)))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /**
     * Collects the category names the sheet uses and gives each one the key the catalog file
     * addresses it by, so a name repeated down the sheet becomes one category.
     */
    private static final class CategoryKeys {
        private final LinkedHashMap<String, CategoryEntry> byName = new LinkedHashMap<>();

        private String keyOf(String name) {
            if (name.isEmpty()) return null;
            return byName.computeIfAbsent(
                            name.toLowerCase(Locale.ROOT),
                            _ -> new CategoryEntry(slug(name, byName.size()), name, "", byName.size()))
                    .key();
        }

        private List<CategoryEntry> entries() {
            return List.copyOf(byName.values());
        }

        private static String slug(String name, int ordinal) {
            String folded = Normalizer.normalize(name.replace("ß", "ss"), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-+|-+$", "");
            return folded.isEmpty() ? FALLBACK_CATEGORY_KEY + "-" + ordinal : folded;
        }
    }

    /**
     * Resolved positions of the mapped columns inside the parsed header row. Unmapped or unknown
     * columns resolve to an empty cell value, which is what makes every column but the question
     * text optional.
     */
    private record ColumnIndex(
            int question,
            int answer,
            int category,
            int type,
            int points,
            int description,
            int image,
            int distractors,
            int pointsPerCorrect,
            int requiredCount,
            int orderedRequired) {

        private static ColumnIndex resolve(List<String> headers, CsvMappings mappings) {
            int question = headers.indexOf(mappings.questionColumn());
            if (question < 0) throw new BadRequestResponse("Question column not found in CSV headers");
            return new ColumnIndex(
                    question,
                    headers.indexOf(mappings.answerColumn()),
                    headers.indexOf(mappings.categoryColumn()),
                    headers.indexOf(mappings.typeColumn()),
                    headers.indexOf(mappings.pointsColumn()),
                    headers.indexOf(mappings.descriptionColumn()),
                    headers.indexOf(mappings.imageColumn()),
                    headers.indexOf(mappings.distractorColumn()),
                    headers.indexOf(mappings.pointsPerCorrectColumn()),
                    headers.indexOf(mappings.requiredCountColumn()),
                    headers.indexOf(mappings.orderedRequiredColumn()));
        }

        private String cell(int index, List<String> cells) {
            return index >= 0 && index < cells.size() ? cells.get(index).trim() : "";
        }
    }

    /**
     * Column mapping and separators chosen in the import wizard. Every column but the question
     * text may be left unmapped, in which case the field keeps its default.
     *
     * @param questionColumn         header naming the question text column
     * @param answerColumn           header naming the answer column
     * @param categoryColumn         header naming the category column
     * @param typeColumn             header naming the question type column
     * @param pointsColumn           header naming the points column
     * @param descriptionColumn      header naming the supplementary text column
     * @param imageColumn            header naming the column carrying an image address
     * @param distractorColumn       header naming the wrong answers offered beside the right ones,
     *                               which is what turns a blank into a list to pick from
     * @param pointsPerCorrectColumn header naming the per-answer point value column
     * @param requiredCountColumn    header naming how many answers an enumeration asks for
     * @param orderedRequiredColumn  header naming whether an enumeration wants them in order
     * @param separator              the column separator, comma when unset
     * @param answerSeparator        the separator between multiple answers in one cell, semicolon
     *                               when unset
     * @param defaultType            the question type for rows without a type cell
     */
    public record CsvMappings(
            String questionColumn,
            String answerColumn,
            String categoryColumn,
            String typeColumn,
            String pointsColumn,
            String descriptionColumn,
            String imageColumn,
            String distractorColumn,
            String pointsPerCorrectColumn,
            String requiredCountColumn,
            String orderedRequiredColumn,
            String separator,
            String answerSeparator,
            QuizQuestionType defaultType) {

        char separatorChar() {
            String value = separator != null ? separator : DEFAULT_SEPARATOR;
            return value.charAt(0);
        }

        String answerSeparatorOrDefault() {
            return answerSeparator != null ? answerSeparator : DEFAULT_ANSWER_SEPARATOR;
        }
    }

    /**
     * A sheet read into the shape a catalog file carries.
     *
     * @param categories the categories the sheet's category column introduced
     * @param questions  one draft per row that carried question text
     */
    public record CsvDraft(List<CategoryEntry> categories, List<DraftQuestion> questions) {}

    /**
     * One drafted question, together with what it was read from. The wizard needs the untouched
     * answer cell to offer splitting it again on a different separator, which is the one correction
     * a sheet with mixed punctuation always needs.
     *
     * @param question        the question as it would be imported
     * @param rawAnswer       the answer cell exactly as the sheet had it
     * @param answerSeparator the separator it was split on
     */
    public record DraftQuestion(QuestionEntry question, String rawAnswer, String answerSeparator) {}
}
