/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.QuizTestSection;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class QuizPdfService {
    private static final Logger log = LoggerFactory.getLogger(QuizPdfService.class);

    private final QuizTestRepository testRepository;
    private final QuizCatalogRepository catalogRepository;
    private final QuizQuestionImageService imageService;

    @Inject
    public QuizPdfService(
            QuizTestRepository testRepository,
            QuizCatalogRepository catalogRepository,
            QuizQuestionImageService imageService) {
        this.testRepository = testRepository;
        this.catalogRepository = catalogRepository;
        this.imageService = imageService;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    public byte[] exportQuestionPdf(int testId) throws IOException, InterruptedException {
        var data = buildExportData(testId);
        var resources = new HashMap<String, byte[]>();
        String typst = generateTypst(data.title, data.sections, data.totalMaxPoints, false, resources);
        return TypstCompiler.compile(typst, resources);
    }

    public byte[] exportSolutionPdf(int testId) throws IOException, InterruptedException {
        var data = buildExportData(testId);
        var resources = new HashMap<String, byte[]>();
        String typst = generateTypst(data.title, data.sections, data.totalMaxPoints, true, resources);
        return TypstCompiler.compile(typst, resources);
    }

    private ExportData buildExportData(int testId) {
        var test = testRepository.findById(testId).orElseThrow();
        var sections = testRepository.findSections(testId);
        var frozenQuestions = testRepository.findFrozenQuestions(testId);

        var questionsBySection = new LinkedHashMap<Integer, List<QuizQuestion>>();
        for (var section : sections) {
            questionsBySection.put(section.id(), new ArrayList<>());
        }
        for (var fq : frozenQuestions) {
            var question = catalogRepository.findQuestionById(fq.questionId());
            question.ifPresent(q -> {
                var sectionId = fq.sectionId() != null
                        ? fq.sectionId()
                        : sections.getFirst().id();
                questionsBySection
                        .computeIfAbsent(sectionId, _ -> new ArrayList<>())
                        .add(q);
            });
        }

        List<SectionData> sectionDataList = new ArrayList<>();
        double totalMaxPoints = 0;
        for (var section : sections) {
            var questions = questionsBySection.getOrDefault(section.id(), List.of());
            double sectionPoints =
                    questions.stream().mapToDouble(QuizQuestion::points).sum();
            totalMaxPoints += sectionPoints;
            sectionDataList.add(new SectionData(section, questions, sectionPoints));
        }

        return new ExportData(test.title(), sectionDataList, totalMaxPoints);
    }

    private String generateTypst(
            String title,
            List<SectionData> sections,
            double totalMaxPoints,
            boolean showAnswers,
            Map<String, byte[]> resources) {
        var sb = new StringBuilder();
        sb.append("#set document(title: \"").append(escape(title)).append("\")\n");
        sb.append("#set page(paper: \"a4\", margin: (top: 2.5cm, bottom: 2cm, left: 2cm, right: 2cm),\n");
        sb.append("  header: align(right)[").append(escape(title));
        if (showAnswers) sb.append(" - Lösungen");
        sb.append("],\n");
        sb.append("  footer: context align(center)[#counter(page).display()])\n");
        sb.append("#set text(font: \"Liberation Sans\", size: 11pt)\n");
        sb.append("#set par(justify: true)\n\n");

        // Title
        sb.append("= ").append(escape(title));
        if (showAnswers) sb.append(" - Lösungen");
        sb.append("\n\n");

        if (!showAnswers) {
            sb.append("#grid(columns: (1fr, 1fr, auto), gutter: 12pt,\n");
            sb.append("  [*Name:* #box(width: 1fr, stroke: (bottom: 0.5pt))[]],\n");
            sb.append("  [*Datum:* #box(width: 1fr, stroke: (bottom: 0.5pt))[]],\n");
            sb.append("  [*Gesamt:* #h(4pt) #box(width: 1cm, stroke: (bottom: 0.5pt))[] \\/ ")
                    .append(totalMaxPoints)
                    .append("P],\n");
            sb.append(")\n\n");
        } else {
            sb.append("#align(right)[*Gesamt: ").append(totalMaxPoints).append(" Punkte*]\n\n");
        }

        int questionNum = 1;
        for (var section : sections) {
            sb.append("== ").append(escape(section.section.title()));
            sb.append(" #h(1fr) #text(size: 9pt, fill: gray)[")
                    .append(section.maxPoints)
                    .append(" Punkte]\n\n");

            for (var q : section.questions) {
                // Wrap entire question in a non-breakable block
                sb.append("#block(breakable: false)[\n");

                // Question title and points in a table row
                sb.append("#table(columns: (1fr, auto), stroke: none, inset: 0pt,\n");
                sb.append("  [*")
                        .append(questionNum++)
                        .append(".* ")
                        .append(escape(q.title()))
                        .append("],\n");
                if (showAnswers) {
                    sb.append("  [#text(size: 9pt, fill: gray)[")
                            .append(q.points())
                            .append("P]],\n");
                } else {
                    sb.append("  [#box(width: 0.8cm, stroke: (bottom: 0.5pt))[] #text(size: 9pt)[\\/ ")
                            .append(q.points())
                            .append("P]],\n");
                }
                sb.append(")\n\n");

                if (q.description() != null && !q.description().isBlank()) {
                    sb.append("#text(size: 9pt, fill: gray)[")
                            .append(escape(q.description()))
                            .append("]\n\n");
                }

                // Render image if present
                if (q.imageUrl() != null && !q.imageUrl().isBlank()) {
                    String imgFile = resolveImage(q.id(), q.catalogId(), questionNum - 1, resources);
                    if (imgFile != null) {
                        sb.append("#image(\"").append(imgFile).append("\", width: 40%)\n\n");
                    }
                }

                try {
                    var cfg = q.configNode();
                    renderQuestion(sb, q.quizQuestionType(), cfg, showAnswers);
                } catch (Exception e) {
                    log.warn("Failed to parse question config for question {}", q.id(), e);
                }

                sb.append("]\n\n"); // close #block
            }

            // Section summary
            if (!showAnswers) {
                sb.append(
                                "#align(right)[#text(size: 10pt)[*Abschnitt:* #box(width: 1cm, stroke: (bottom: 0.5pt))[] *\\/* *")
                        .append(section.maxPoints)
                        .append(" Punkte*]]\n\n");
            }
        }
        return sb.toString();
    }

    private void renderQuestion(StringBuilder sb, QuizQuestionType type, JsonNode cfg, boolean showAnswers) {
        switch (type) {
            case MULTIPLE_CHOICE -> renderMultipleChoice(sb, cfg, showAnswers);
            case TRUE_FALSE -> renderTrueFalse(sb, cfg, showAnswers);
            case FREE_ANSWER -> renderFreeAnswer(sb, cfg, showAnswers);
            case FILL_IN_THE_BLANK -> renderFillBlank(sb, cfg, showAnswers);
            case CONNECT -> renderConnect(sb, cfg, showAnswers);
            case ORDERING -> renderOrdering(sb, cfg, showAnswers);
            case IMAGE_TEXT -> renderImageText(sb, cfg, showAnswers);
            case ENUMERATION -> renderEnumeration(sb, cfg, showAnswers);
        }
    }

    private void renderMultipleChoice(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        var options = cfg.get("options");
        if (options == null || !options.isArray()) return;

        List<int[]> indexed = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            indexed.add(new int[] {i});
        }
        if (!showAnswers) {
            Collections.shuffle(indexed);
        }

        for (var idx : indexed) {
            var opt = options.get(idx[0]);
            String text = opt.get("text").asString();
            boolean correct = opt.has("correct") && opt.get("correct").asBoolean();
            if (showAnswers) {
                sb.append(
                                correct
                                        ? "#box(stroke: 0.5pt, width: 10pt, height: 10pt, align(center)[x]) #h(4pt) *"
                                        : "#box(stroke: 0.5pt, width: 10pt, height: 10pt) #h(4pt) ")
                        .append(escape(text));
                if (correct) sb.append("*");
                sb.append("\n\n");
            } else {
                sb.append("#box(stroke: 0.5pt, width: 10pt, height: 10pt) #h(4pt) ")
                        .append(escape(text))
                        .append("\n\n");
            }
        }
    }

    private void renderTrueFalse(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        boolean correct = cfg.has("correctAnswer") && cfg.get("correctAnswer").asBoolean();
        if (showAnswers) {
            sb.append(
                            correct
                                    ? "#box(stroke: 0.5pt, width: 10pt, height: 10pt, align(center)[x]) #h(4pt) *Wahr*"
                                    : "#box(stroke: 0.5pt, width: 10pt, height: 10pt) #h(4pt) Wahr")
                    .append(" #h(16pt) ");
            sb.append(
                            !correct
                                    ? "#box(stroke: 0.5pt, width: 10pt, height: 10pt, align(center)[x]) #h(4pt) *Falsch*"
                                    : "#box(stroke: 0.5pt, width: 10pt, height: 10pt) #h(4pt) Falsch")
                    .append("\n\n");
        } else {
            sb.append(
                    "#box(stroke: 0.5pt, width: 10pt, height: 10pt) #h(4pt) Wahr #h(16pt) #box(stroke: 0.5pt, width: 10pt, height: 10pt) #h(4pt) Falsch\n\n");
        }
    }

    private void renderFreeAnswer(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        if (showAnswers) {
            var answers = cfg.get("answers");
            if (answers != null && answers.isArray() && !answers.isEmpty()) {
                sb.append("*Mögliche Antworten:*\n");
                for (var a : answers) {
                    sb.append("- ").append(escape(a.asString())).append("\n");
                }
            }
        } else {
            int lines = cfg.has("lines") ? cfg.get("lines").asInt() : 3;
            sb.append("#v(8pt)\n");
            sb.repeat("#line(length: 100%, stroke: 0.5pt)\n#v(16pt)\n", Math.max(0, lines));
        }
        sb.append("\n");
    }

    private void renderFillBlank(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        var answers = cfg.get("answers");
        var distractors = cfg.get("distractors");
        var textNode = cfg.get("text");
        if (showAnswers) {
            boolean hasDistractorsSol = distractors != null && distractors.isArray() && !distractors.isEmpty();

            if (hasDistractorsSol
                    && textNode != null
                    && !textNode.asString().isBlank()
                    && answers != null
                    && answers.isArray()) {
                // Word bank mode: build the word list to find the correct number for each answer
                List<String> allWords = new ArrayList<>();
                for (var a : answers) allWords.add(a.asString());
                for (var d : distractors) allWords.add(d.asString());
                String text = textNode.asString();
                renderGappedText(sb, text, (out, ansIdx) -> {
                    String answer =
                            ansIdx < answers.size() ? answers.get(ansIdx).asString() : "?";
                    int wordNum = allWords.indexOf(answer) + 1;
                    out.append(" *")
                            .append(escape(answer))
                            .append(" (")
                            .append(wordNum)
                            .append(")* ");
                });
            } else if (textNode != null && !textNode.asString().isBlank() && answers != null && answers.isArray()) {
                String text = textNode.asString();
                renderGappedText(sb, text, (out, ansIdx) -> {
                    String answer =
                            ansIdx < answers.size() ? answers.get(ansIdx).asString() : "?";
                    out.append(" *").append(escape(answer)).append("* ");
                });
            } else if (answers != null && answers.isArray()) {
                // Fallback: just list the answers
                List<String> parts = new ArrayList<>();
                for (int i = 0; i < answers.size(); i++) {
                    parts.add(answers.get(i).asString());
                }
                sb.append("*Lücken:* ").append(String.join(", ", parts)).append("\n\n");
            }
        } else {
            // Check if we have distractors (= word bank mode)
            boolean hasDistractors = distractors != null && distractors.isArray() && !distractors.isEmpty();

            if (hasDistractors) {
                // Word bank mode: show highlighted word list and text with numbered gaps
                List<String> allWords = new ArrayList<>();
                if (answers != null && answers.isArray()) for (var a : answers) allWords.add(a.asString());
                for (var d : distractors) allWords.add(d.asString());
                Collections.shuffle(allWords);
                sb.append("#rect(fill: luma(240), radius: 4pt, inset: 8pt)[#text(size: 9pt)[\n");
                sb.append("*Wörter:* ");
                for (int i = 0; i < allWords.size(); i++) {
                    if (i > 0) sb.append(" #h(8pt) | #h(8pt) ");
                    sb.append("(").append(i + 1).append(") ").append(escape(allWords.get(i)));
                }
                sb.append("\n]]\n\n");

                // Show text with numbered gaps
                if (textNode != null && !textNode.asString().isBlank()) {
                    renderFillBlankText(sb, textNode.asString(), true, null);
                }
            } else {
                // No word bank: show text with inline blank lines sized to answer length
                if (textNode != null && !textNode.asString().isBlank() && answers != null && answers.isArray()) {
                    renderFillBlankText(sb, textNode.asString(), false, answers);
                } else {
                    // Fallback: plain gap lines
                    int gapCount = answers != null ? answers.size() : 1;
                    for (int i = 1; i <= gapCount; i++) {
                        sb.append("Lücke ").append(i).append(": #line(length: 60%, stroke: 0.5pt)\n\n");
                    }
                }
            }
        }
    }

    private void renderConnect(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        var pairs = cfg.get("pairs");
        if (pairs == null || !pairs.isArray()) return;

        List<String> left = new ArrayList<>();
        List<String> right = new ArrayList<>();
        for (var pair : pairs) {
            left.add(pair.get("left").asString());
            right.add(pair.get("right").asString());
        }

        if (showAnswers) {
            for (int i = 0; i < left.size(); i++) {
                sb.append("- ").append(escape(left.get(i)));
                sb.append(" → ").append(escape(right.get(i))).append("\n");
            }
        } else {
            // Shuffle right side for paper - layout two columns with space for drawing lines
            List<String> shuffledRight = new ArrayList<>(right);
            Collections.shuffle(shuffledRight);

            sb.append("#grid(columns: (auto, 4cm, auto), row-gutter: 14pt, column-gutter: 6pt,\n");
            sb.append("  [*Links*], [], [*Rechts*],\n");
            int maxRows = Math.max(left.size(), shuffledRight.size());
            for (int i = 0; i < maxRows; i++) {
                String l = i < left.size() ? escape(left.get(i)) : "";
                String r = i < shuffledRight.size() ? escape(shuffledRight.get(i)) : "";
                sb.append("  [").append(l).append("],");
                sb.append(" [],"); // empty column for drawing lines
                sb.append(" [").append(r).append("],\n");
            }
            sb.append(")\n\n");
        }
    }

    private void renderOrdering(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        var items = cfg.get("items");
        if (items == null || !items.isArray()) return;
        if (showAnswers) {
            int i = 1;
            for (var item : items) {
                sb.append(i++).append(". ").append(escape(item.asString())).append("\n");
            }
        } else {
            // Shuffled items, each on its own line with a number field
            List<String> shuffled = new ArrayList<>();
            for (var item : items) shuffled.add(item.asString());
            Collections.shuffle(shuffled);
            for (var item : shuffled) {
                sb.append("#box(stroke: 0.5pt, width: 10pt, height: 10pt) #h(4pt) ")
                        .append(escape(item))
                        .append("\n\n");
            }
        }
        sb.append("\n");
    }

    private void renderImageText(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        if (showAnswers) {
            String answer = cfg.has("answer") ? cfg.get("answer").asString() : "";
            sb.append("*Antwort:* ").append(escape(answer)).append("\n\n");
        } else {
            sb.append("#v(8pt)\n");
            sb.append("#line(length: 100%, stroke: 0.5pt)\n#v(12pt)\n");
            sb.append("#line(length: 100%, stroke: 0.5pt)\n\n");
        }
    }

    private void renderEnumeration(StringBuilder sb, JsonNode cfg, boolean showAnswers) {
        int requiredCount = cfg.has("requiredCount") ? cfg.get("requiredCount").asInt() : 3;
        boolean ordered =
                cfg.has("orderedRequired") && cfg.get("orderedRequired").asBoolean();
        var answers = cfg.get("answers");
        if (showAnswers) {
            sb.append("*Mögliche Antworten (").append(requiredCount).append(" gefragt):*\n");
            if (answers != null && answers.isArray()) {
                int i = 1;
                for (var a : answers) {
                    sb.append(i++).append(". ").append(escape(a.asString())).append("\n");
                }
            }
            if (ordered) {
                sb.append("\n#text(size: 9pt, fill: gray)[Reihenfolge ist relevant.]\n");
            }
        } else {
            if (ordered) {
                sb.append("#text(size: 9pt, fill: gray)[Reihenfolge beachten!]\n\n");
            }
            sb.append("#v(4pt)\n");
            for (int i = 1; i <= requiredCount; i++) {
                sb.append(i).append(". #box(width: 1fr, stroke: (bottom: 0.5pt))[]\n#v(12pt)\n");
            }
        }
        sb.append("\n");
    }

    private void renderFillBlankText(StringBuilder sb, String text, boolean numbered, JsonNode answers) {
        renderGappedText(sb, text, (out, ansIdx) -> {
            if (numbered) {
                out.append("#box(width: 1.5cm, stroke: (bottom: 0.5pt))[]");
            } else {
                int ansLen = answers != null && ansIdx < answers.size()
                        ? answers.get(ansIdx).asString().length()
                        : 5;
                double cm = Math.max(1.5, (ansLen + 2) * 0.25);
                out.append("#box(width: ").append(String.format("%.1f", cm)).append("cm, stroke: (bottom: 0.5pt))[]");
            }
        });
    }

    /**
     * Renders a fill-in-the-blank sentence, delegating each {@code __} gap to the supplied renderer
     * while automatically escaping and appending the surrounding text segments.
     */
    private void renderGappedText(StringBuilder sb, String text, GapRenderer renderer) {
        int ansIdx = 0;
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '_' && i + 1 < text.length() && text.charAt(i + 1) == '_') {
                sb.append(escape(segment.toString()));
                segment.setLength(0);
                while (i < text.length() && text.charAt(i) == '_') i++;
                i--;
                renderer.renderGap(sb, ansIdx);
                ansIdx++;
            } else {
                segment.append(text.charAt(i));
            }
        }
        sb.append(escape(segment.toString())).append("\n\n");
    }

    /**
     * Renders the content of a single fill-in-the-blank gap, given the running answer index.
     */
    @FunctionalInterface
    private interface GapRenderer {
        void renderGap(StringBuilder sb, int ansIdx);
    }

    private String resolveImage(int questionId, int catalogId, int questionNum, Map<String, byte[]> resources) {
        var catalog = catalogRepository.findById(catalogId).orElse(null);
        if (catalog == null) return null;
        var image = imageService.read(catalog.stationId(), questionId, 0).orElse(null);
        if (image == null) return null;
        String filename = "img-" + questionNum + extensionFor(image.contentType());
        resources.put(filename, image.data());
        return filename;
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("#", "\\#")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private record ExportData(String title, List<SectionData> sections, double totalMaxPoints) {}

    private record SectionData(QuizTestSection section, List<QuizQuestion> questions, double maxPoints) {}
}
