/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.util.CsvParser;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns an uploaded CSV sheet into quiz questions. Resolves the column mapping, derives
 * the question type and points per row, reuses or creates the named categories, and
 * builds the typed config each question type expects from the answer cell.
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

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;

    @Inject
    public QuizImportService(QuizCatalogService catalogService, QuizQuestionService questionService) {
        this.catalogService = catalogService;
        this.questionService = questionService;
    }

    /**
     * Imports every mapped row of a CSV sheet as a question of the given catalog.
     *
     * @param catalog    the catalog receiving the questions
     * @param csvContent the decoded sheet
     * @param mappings   which column carries which field, plus the parsing separators
     * @return how many questions were created
     * @throws BadRequestResponse when the sheet cannot be parsed, the question column is
     *                            missing, or a row names an unknown question type
     */
    public ImportResult importCsv(QuizCatalog catalog, String csvContent, CsvMappings mappings) {
        var parsed = parse(catalog.id(), csvContent, mappings.separatorChar());
        var columns = ColumnIndex.resolve(parsed.headers(), mappings);
        var categories = new CategoryResolver(catalog.stationId());

        int imported = 0;
        var rows = parsed.rows();
        for (int i = 0; i < rows.size(); i++) {
            var cells = rows.get(i);
            String title = columns.questionCell(cells);
            if (title.isEmpty()) continue;

            var type = resolveType(columns.typeCell(cells), mappings.defaultType());
            var config = buildConfig(type, columns.answerCell(cells), mappings.answerSeparatorOrDefault());
            questionService.createQuestion(CreateQuestionCommand.builder(catalog.id(), type, title)
                    .category(categories.resolve(columns.categoryCell(cells)))
                    .points(resolvePoints(columns.pointsCell(cells)))
                    .autoPoints(true)
                    .config(config)
                    .position(i)
                    .build());
            imported++;
        }
        log.info("Imported {} questions into quiz catalog {} from CSV", imported, catalog.id());
        return new ImportResult(imported);
    }

    private CsvParser.ParsedCsv parse(int catalogId, String csvContent, char separator) {
        try {
            return CsvParser.parse(csvContent, separator);
        } catch (IOException e) {
            log.warn("Failed to parse CSV for catalog {}", catalogId, e);
            throw new BadRequestResponse("Failed to parse CSV");
        }
    }

    private QuizQuestionType resolveType(String cell, QuizQuestionType fallback) {
        if (!cell.isEmpty()) return parseQuestionType(cell);
        return fallback != null ? fallback : QuizQuestionType.MULTIPLE_CHOICE;
    }

    private double resolvePoints(String cell) {
        if (cell.isEmpty()) return DEFAULT_POINTS;
        try {
            return Double.parseDouble(cell);
        } catch (NumberFormatException e) {
            return DEFAULT_POINTS;
        }
    }

    /**
     * Maps the type spellings the sheets in the field actually use — English and German,
     * spelled out or abbreviated — onto the question types.
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
     * Builds the typed config for a question type from the answer cell. The first entry
     * of a multiple-choice cell is the correct option; connect pairs are written as
     * {@code left=right}.
     */
    private QuestionConfig buildConfig(QuizQuestionType type, String answer, String answerSeparator) {
        var parts = split(answer, answerSeparator);
        return switch (type) {
            case MULTIPLE_CHOICE -> multipleChoice(parts);
            case TRUE_FALSE -> new QuestionConfig.TrueFalse(isAffirmative(answer));
            case FREE_ANSWER -> new QuestionConfig.FreeAnswer(parts, FREE_ANSWER_LINES, 0);
            case FILL_IN_THE_BLANK -> new QuestionConfig.FillInTheBlank("", parts, List.of(), false, 0);
            case CONNECT -> connect(parts);
            case ORDERING -> new QuestionConfig.Ordering(parts, 0);
            case IMAGE_TEXT -> new QuestionConfig.ImageText(null, null);
            case ENUMERATION ->
                new QuestionConfig.Enumeration(parts, Math.min(ENUMERATION_REQUIRED_COUNT, parts.size()), false, 0);
        };
    }

    private QuestionConfig multipleChoice(List<String> parts) {
        var options = new ArrayList<QuestionConfig.MultipleChoice.Option>();
        for (int i = 0; i < parts.size(); i++) {
            options.add(new QuestionConfig.MultipleChoice.Option(parts.get(i), i == 0));
        }
        return new QuestionConfig.MultipleChoice(options, MULTIPLE_CHOICE_POINTS_PER_CORRECT);
    }

    private QuestionConfig connect(List<String> parts) {
        var pairs = parts.stream()
                .map(part -> part.split("=", 2))
                .map(split -> new QuestionConfig.Connect.Pair(split[0].trim(), split.length > 1 ? split[1].trim() : ""))
                .toList();
        return new QuestionConfig.Connect(pairs, 0);
    }

    private boolean isAffirmative(String answer) {
        return answer.equalsIgnoreCase("true") || answer.equals("1") || answer.equalsIgnoreCase("wahr");
    }

    private List<String> split(String answer, String separator) {
        return Arrays.stream(answer.split(Pattern.quote(separator)))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    /**
     * Reuses the station's existing categories by name and creates the ones the sheet
     * introduces, so repeated names in the sheet map onto a single category.
     */
    private final class CategoryResolver {
        private final int stationId;
        private final int initialCount;
        private final HashMap<String, Integer> byName = new HashMap<>();

        private CategoryResolver(int stationId) {
            this.stationId = stationId;
            var existing = catalogService.findCategories(stationId);
            this.initialCount = existing.size();
            for (var category : existing) {
                byName.put(category.name().toLowerCase(), category.id());
            }
        }

        private Integer resolve(String name) {
            if (name.isEmpty()) return null;
            return byName.computeIfAbsent(name.toLowerCase(), _ -> catalogService
                    .createCategory(stationId, name, "", initialCount)
                    .id());
        }
    }

    /**
     * Resolved positions of the mapped columns inside the parsed header row. Unmapped
     * or unknown columns resolve to an empty cell value.
     */
    private record ColumnIndex(int question, int answer, int category, int type, int points) {

        private static ColumnIndex resolve(List<String> headers, CsvMappings mappings) {
            int question = headers.indexOf(mappings.questionColumn());
            if (question < 0) throw new BadRequestResponse("Question column not found in CSV headers");
            return new ColumnIndex(
                    question,
                    headers.indexOf(mappings.answerColumn()),
                    headers.indexOf(mappings.categoryColumn()),
                    headers.indexOf(mappings.typeColumn()),
                    headers.indexOf(mappings.pointsColumn()));
        }

        private String questionCell(List<String> cells) {
            return cell(question, cells);
        }

        private String answerCell(List<String> cells) {
            return cell(answer, cells);
        }

        private String categoryCell(List<String> cells) {
            return cell(category, cells);
        }

        private String typeCell(List<String> cells) {
            return cell(type, cells);
        }

        private String pointsCell(List<String> cells) {
            return cell(points, cells);
        }

        private String cell(int index, List<String> cells) {
            return index >= 0 && index < cells.size() ? cells.get(index).trim() : "";
        }
    }

    /**
     * Column mapping and separators chosen in the import wizard.
     *
     * @param questionColumn  header naming the question text column
     * @param answerColumn    header naming the answer column
     * @param categoryColumn  header naming the category column
     * @param typeColumn      header naming the question type column
     * @param pointsColumn    header naming the points column
     * @param separator       the column separator, comma when unset
     * @param answerSeparator the separator between multiple answers in one cell,
     *                        semicolon when unset
     * @param defaultType     the question type for rows without a type cell
     */
    public record CsvMappings(
            String questionColumn,
            String answerColumn,
            String categoryColumn,
            String typeColumn,
            String pointsColumn,
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
     * @param imported how many questions the import created
     */
    public record ImportResult(int imported) {}
}
