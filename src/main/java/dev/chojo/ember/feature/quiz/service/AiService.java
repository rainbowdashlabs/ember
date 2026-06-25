/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentHashMap<String, String> PROMPT_CACHE = new ConcurrentHashMap<>();
    private static final Path PROMPTS_DIR = Path.of("templates", "ai-prompts");

    private final AiProviderRepository providerRepository;

    @Inject
    public AiService(AiProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    // -- Provider Management --

    private static boolean isChatModel(String id) {
        if (id.contains("embedding")
                || id.contains("whisper")
                || id.contains("tts")
                || id.contains("dall-e")
                || id.contains("davinci")
                || id.contains("babbage")
                || id.contains("moderation")
                || id.contains("search")
                || id.contains("similarity")
                || id.contains("code-")
                || id.contains("text-")
                || id.contains("instruct")
                || id.contains(":ft-")) {
            return false;
        }
        return id.startsWith("gpt-4")
                || id.startsWith("gpt-3.5")
                || id.startsWith("o1")
                || id.startsWith("o3")
                || id.startsWith("o4")
                || id.startsWith("chatgpt");
    }

    public List<StationAiProvider> getProviders(int stationId) {
        return providerRepository.findByStation(stationId);
    }

    public void saveProvider(int stationId, String provider, String apiKey, String model) {
        providerRepository.upsert(stationId, provider, apiKey, model);
    }

    public void deleteProvider(int stationId, String provider) {
        providerRepository.delete(stationId, provider);
    }

    public String getPrompt(int stationId) {
        return providerRepository.getPrompt(stationId).orElse(getDefaultPrompt());
    }

    public void setPrompt(int stationId, String prompt) {
        providerRepository.setPrompt(stationId, prompt);
    }

    // -- Prompt Loading --

    public String getDefaultPrompt() {
        return loadPromptFile("default_user_prompt", "de");
    }

    // -- Key/Model Resolution --

    public ChatSession createQuestionSession(
            int stationId,
            String provider,
            String transientKey,
            String model,
            QuizQuestionType quizQuestionType,
            String userPrompt,
            String locale,
            String categoryName,
            String categoryDescription,
            List<String> existingTitles) {
        String apiKey = resolveApiKey(stationId, provider, transientKey);
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("No API key available");
        String resolvedModel = resolveModel(stationId, provider, model);
        String effectiveLocale = locale != null ? locale : "de";

        String systemPrompt =
                loadPromptFile(quizQuestionType.promptFile(), effectiveLocale).replace("{count}", "1");
        systemPrompt +=
                "\n\nWICHTIG: Erstelle KEINE Fragen die identisch oder ähnlich zu bereits genannten Fragen sind.";

        var session = new ChatSession(provider, apiKey, resolvedModel, systemPrompt);

        // First user message: context with all existing titles + user instructions
        var context = new StringBuilder();
        String effectiveUserPrompt = (userPrompt != null && !userPrompt.isBlank()) ? userPrompt : getPrompt(stationId);
        context.append(effectiveUserPrompt);
        if (categoryName != null && !categoryName.isBlank()) {
            context.append("\n\nKategorie: ").append(categoryName);
            if (categoryDescription != null && !categoryDescription.isBlank()) {
                context.append("\nBeschreibung: ").append(categoryDescription);
            }
        }
        if (!existingTitles.isEmpty()) {
            context.append("\n\nBereits existierende Fragen (NICHT wiederholen):\n");
            for (var title : existingTitles) {
                context.append("- ").append(title).append("\n");
            }
        }
        context.append("\n\nErstelle genau 1 neue, einzigartige Frage.");
        session.addUserMessage(context.toString());

        return session;
    }

    public List<GeneratedQuestion> generateNextQuestion(ChatSession session, QuizQuestionType quizQuestionType) {
        try {
            String text = chatWithSession(session);
            session.addAssistantMessage(text);
            var parsed = parseAndValidate(text, quizQuestionType);
            if (!parsed.isEmpty()) {
                // Next turn: just ask for another one — AI has full context
                session.addUserMessage("Erstelle eine weitere einzigartige Frage. Wiederhole keine der bisherigen.");
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Session-based generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    // -- Chat Completion (unified) --

    public List<String> generate(
            int stationId,
            String provider,
            String transientKey,
            String model,
            String question,
            String correctAnswer,
            int count) {
        String apiKey = resolveApiKey(stationId, provider, transientKey);
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("No API key available");
        String resolvedModel = resolveModel(stationId, provider, model);
        String systemPrompt = loadPromptFile("wrong_answers", "de").replace("{count}", String.valueOf(count));
        String userPrompt = getPrompt(stationId);
        String fullUserMessage = userPrompt + "\n\nFrage: " + question + "\nRichtige Antwort: " + correctAnswer;

        try {
            String text = chat(provider, apiKey, resolvedModel, systemPrompt, fullUserMessage);
            return text.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty())
                    .limit(count)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI generation failed for provider {}", provider, e);
            throw new RuntimeException("AI generation failed: " + e.getMessage());
        }
    }

    public List<ModelInfo> fetchModels(int stationId, String provider, String transientKey) {
        String apiKey = resolveApiKey(stationId, provider, transientKey);
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("No API key available");
        try {
            return switch (provider) {
                case "openai" -> fetchOpenAiModels(apiKey);
                case "gemini" -> fetchGeminiModels(apiKey);
                case "claude" -> fetchClaudeModels(apiKey);
                default -> List.of();
            };
        } catch (Exception e) {
            log.error("Failed to fetch models for provider {}", provider, e);
            throw new RuntimeException("Failed to fetch models: " + e.getMessage());
        }
    }

    private String loadPromptFile(String name, String locale) {
        return PROMPT_CACHE.computeIfAbsent(locale + "/" + name, _ -> {
            Path path = PROMPTS_DIR.resolve(locale).resolve(name + ".txt");
            try {
                if (Files.exists(path)) return Files.readString(path);
            } catch (IOException ignored) {
            }
            Path fallback = PROMPTS_DIR.resolve("de").resolve(name + ".txt");
            try {
                if (Files.exists(fallback)) return Files.readString(fallback);
            } catch (IOException ignored) {
            }
            log.warn("Prompt template not found: {}", path);
            return "";
        });
    }

    private String resolveApiKey(int stationId, String provider, String transientKey) {
        if (transientKey != null && !transientKey.isBlank()) return transientKey;
        return providerRepository
                .findByProvider(stationId, provider)
                .map(StationAiProvider::apiKey)
                .orElse(null);
    }

    private String resolveModel(int stationId, String provider, String requestModel) {
        if (requestModel != null && !requestModel.isBlank()) return requestModel;
        return providerRepository
                .findByProvider(stationId, provider)
                .map(StationAiProvider::model)
                .filter(m -> !m.isBlank())
                .orElseGet(() -> switch (provider) {
                    case "openai" -> "gpt-4o-mini";
                    case "gemini" -> "gemini-2.0-flash";
                    case "claude" -> "claude-sonnet-4-20250514";
                    default -> "gpt-4o-mini";
                });
    }

    private String chat(String provider, String apiKey, String model, String systemPrompt, String userMessage) {
        return switch (provider) {
            case "openai" -> chatOpenAi(apiKey, model, systemPrompt, userMessage);
            case "gemini" -> chatGemini(apiKey, model, systemPrompt, userMessage);
            case "claude" -> chatClaude(apiKey, model, systemPrompt, userMessage);
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    private String chatWithSession(ChatSession session) {
        return switch (session.provider()) {
            case "openai" -> chatOpenAiSession(session);
            case "gemini" -> chatGeminiSession(session);
            case "claude" -> chatClaudeSession(session);
            default -> throw new IllegalArgumentException("Unknown provider: " + session.provider());
        };
    }

    private String chatOpenAiSession(ChatSession session) {
        OpenAIClient client =
                OpenAIOkHttpClient.builder().apiKey(session.apiKey()).build();
        var builder = ChatCompletionCreateParams.builder()
                .model(session.model())
                .addSystemMessage(session.systemPrompt())
                .temperature(0.8);
        for (var msg : session.messages()) {
            if ("user".equals(msg.role())) builder.addUserMessage(msg.content());
            else if ("assistant".equals(msg.role())) builder.addAssistantMessage(msg.content());
        }
        var completion = client.chat().completions().create(builder.build());
        return completion.choices().getFirst().message().content().orElse("");
    }

    private String chatGeminiSession(ChatSession session) {
        // Gemini doesn't have native multi-turn via this SDK — concatenate context
        var fullMessage = new StringBuilder();
        for (var msg : session.messages()) {
            fullMessage
                    .append(msg.role().equals("user") ? "User: " : "Assistant: ")
                    .append(msg.content())
                    .append("\n\n");
        }
        return chatGemini(session.apiKey(), session.model(), session.systemPrompt(), fullMessage.toString());
    }

    // -- Wrong Answer Generation --

    private String chatClaudeSession(ChatSession session) {
        AnthropicClient client =
                AnthropicOkHttpClient.builder().apiKey(session.apiKey()).build();
        var builder = MessageCreateParams.builder()
                .model(session.model())
                .maxTokens(2048L)
                .system(session.systemPrompt());
        for (var msg : session.messages()) {
            if ("user".equals(msg.role())) builder.addUserMessage(msg.content());
            else if ("assistant".equals(msg.role())) builder.addAssistantMessage(msg.content());
        }
        var message = client.messages().create(builder.build());
        return message.content().stream()
                .filter(ContentBlock::isText)
                .map(b -> b.asText().text())
                .findFirst()
                .orElse("");
    }

    // -- Question Generation --

    private List<GeneratedQuestion> parseAndValidate(String text, QuizQuestionType quizQuestionType) throws Exception {
        // Strip markdown fences
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n') + 1;
            int end = text.lastIndexOf("```");
            if (end > start) text = text.substring(start, end).trim();
        }

        var array = MAPPER.readTree(text);
        if (!array.isArray()) throw new RuntimeException("AI did not return a JSON array");

        var results = new ArrayList<GeneratedQuestion>();
        for (var item : array) {
            String title = item.has("title") ? item.get("title").asText() : "";
            if (title.isBlank()) continue;
            if (!item.has("config")) continue;

            var configNode = item.get("config");
            String configJson = MAPPER.writeValueAsString(configNode);

            // Validate by mapping to the typed POJO
            if (validateConfig(configJson, quizQuestionType)) {
                results.add(new GeneratedQuestion(title, configJson));
            } else {
                log.debug("Skipping invalid question '{}' for type {}", title, quizQuestionType);
            }
        }
        return results;
    }

    private boolean validateConfig(String configJson, QuizQuestionType quizQuestionType) {
        try {
            return switch (quizQuestionType) {
                case MULTIPLE_CHOICE -> {
                    var cfg = MAPPER.readValue(configJson, QuestionConfig.MultipleChoice.class);
                    yield cfg.options() != null
                            && cfg.options().size() >= 2
                            && cfg.options().stream().anyMatch(QuestionConfig.MultipleChoice.Option::correct)
                            && cfg.options().stream()
                                    .allMatch(o -> o.text() != null && !o.text().isBlank());
                }
                case TRUE_FALSE -> {
                    MAPPER.readValue(configJson, QuestionConfig.TrueFalse.class);
                    yield true;
                }
                case FREE_ANSWER -> {
                    var cfg = MAPPER.readValue(configJson, QuestionConfig.FreeAnswer.class);
                    yield cfg.lines() > 0;
                }
                case FILL_IN_THE_BLANK -> {
                    var cfg = MAPPER.readValue(configJson, QuestionConfig.FillInTheBlank.class);
                    yield cfg.text() != null
                            && !cfg.text().isBlank()
                            && cfg.answers() != null
                            && !cfg.answers().isEmpty();
                }
                case CONNECT -> {
                    var cfg = MAPPER.readValue(configJson, QuestionConfig.Connect.class);
                    yield cfg.pairs() != null
                            && cfg.pairs().size() >= 2
                            && cfg.pairs().stream()
                                    .allMatch(p -> p.left() != null
                                            && !p.left().isBlank()
                                            && p.right() != null
                                            && !p.right().isBlank());
                }
                case ORDERING -> {
                    var cfg = MAPPER.readValue(configJson, QuestionConfig.Ordering.class);
                    yield cfg.items() != null
                            && cfg.items().size() >= 2
                            && cfg.items().stream().allMatch(i -> i != null && !i.isBlank());
                }
                case IMAGE_TEXT -> true;
                case ENUMERATION -> {
                    var cfg = MAPPER.readTree(configJson);
                    yield cfg.has("answers")
                            && cfg.get("answers").isArray()
                            && !cfg.get("answers").isEmpty();
                }
            };
        } catch (Exception e) {
            log.debug("Config validation failed for type {}: {}", quizQuestionType, e.getMessage());
            return false;
        }
    }

    private String chatOpenAi(String apiKey, String model, String systemPrompt, String userMessage) {
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
        var params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(systemPrompt)
                .addUserMessage(userMessage)
                .temperature(0.8)
                .build();
        var completion = client.chat().completions().create(params);
        return completion.choices().getFirst().message().content().orElse("");
    }

    // -- Model Listing --

    private List<ModelInfo> fetchOpenAiModels(String apiKey) {
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
        var page = client.models().list();
        var result = new ArrayList<ModelInfo>();
        for (var m : page.data()) {
            if (isChatModel(m.id())) {
                result.add(new ModelInfo(m.id(), m.id()));
            }
        }
        result.sort(Comparator.comparing(ModelInfo::name));
        return result;
    }

    private String chatGemini(String apiKey, String model, String systemPrompt, String userMessage) {
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            var config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                    .build();
            var response = client.models.generateContent(model, userMessage, config);
            return response.text();
        }
    }

    // ============================================================
    //  OpenAI SDK
    // ============================================================

    private List<ModelInfo> fetchGeminiModels(String apiKey) {
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            var response = client.models.list(null);
            var result = new ArrayList<ModelInfo>();
            for (var m : response) {
                String id = m.name().orElse("");
                if (id.startsWith("models/")) id = id.substring(7);
                if (id.isEmpty()) continue;
                String name = m.displayName().orElse(id);
                result.add(new ModelInfo(id, name));
            }
            result.sort(Comparator.comparing(ModelInfo::name));
            return result;
        }
    }

    private String chatClaude(String apiKey, String model, String systemPrompt, String userMessage) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        var params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(2048L)
                .system(systemPrompt)
                .addUserMessage(userMessage)
                .build();
        var message = client.messages().create(params);
        return message.content().stream()
                .filter(ContentBlock::isText)
                .map(b -> b.asText().text())
                .findFirst()
                .orElse("");
    }

    // ============================================================
    //  Gemini SDK (Google GenAI)
    // ============================================================

    private List<ModelInfo> fetchClaudeModels(String apiKey) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        var page = client.models().list();
        var result = new ArrayList<ModelInfo>();
        for (var m : page.data()) {
            m.displayName();
            result.add(new ModelInfo(m.id(), m.displayName()));
        }
        result.sort(Comparator.comparing(ModelInfo::name));
        return result;
    }

    public record ChatMessage(String role, String content) {}

    // ============================================================
    //  Claude SDK (Anthropic)
    // ============================================================

    public static class ChatSession {
        private final String provider;
        private final String apiKey;
        private final String model;
        private final String systemPrompt;
        private final List<ChatMessage> messages = new ArrayList<>();

        ChatSession(String provider, String apiKey, String model, String systemPrompt) {
            this.provider = provider;
            this.apiKey = apiKey;
            this.model = model;
            this.systemPrompt = systemPrompt;
        }

        public List<ChatMessage> messages() {
            return messages;
        }

        public String provider() {
            return provider;
        }

        public String apiKey() {
            return apiKey;
        }

        public String model() {
            return model;
        }

        public String systemPrompt() {
            return systemPrompt;
        }

        void addUserMessage(String content) {
            messages.add(new ChatMessage("user", content));
        }

        void addAssistantMessage(String content) {
            messages.add(new ChatMessage("assistant", content));
        }
    }

    public record GeneratedQuestion(String title, String config) {}

    // ============================================================
    //  Utilities
    // ============================================================

    public record ModelInfo(String id, String name) {}
}
