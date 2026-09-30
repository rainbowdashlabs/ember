/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.MessageCreateParams;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import dev.chojo.ember.util.Json;
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

/**
 * Generates quiz questions and wrong answers through the AI vendor a station configured.
 *
 * <p>Every vendor client is built for one call and closed when the call ends, whether it
 * succeeded or not: each owns a connection pool and dispatcher threads that would otherwise
 * outlive the request.
 */
@Singleton
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final ConcurrentHashMap<String, String> PROMPT_CACHE = new ConcurrentHashMap<>();
    private static final Path PROMPTS_DIR = Path.of("templates", "ai-prompts");

    private final AiProviderRepository providerRepository;
    private final AiClientFactory clients;
    private final AiCredentialService credentials;

    @Inject
    public AiService(
            AiProviderRepository providerRepository, AiClientFactory clients, AiCredentialService credentials) {
        this.providerRepository = providerRepository;
        this.clients = clients;
        this.credentials = credentials;
    }

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
        log.info("Saved AI provider {} (model {}) for station {}", provider, model, stationId);
    }

    public void deleteProvider(int stationId, String provider) {
        providerRepository.delete(stationId, provider);
        log.info("Deleted AI provider {} for station {}", provider, stationId);
    }

    public String getPrompt(int stationId) {
        return providerRepository.getPrompt(stationId).orElse(getDefaultPrompt());
    }

    public void setPrompt(int stationId, String prompt) {
        providerRepository.setPrompt(stationId, prompt);
        log.info("Updated AI prompt for station {}", stationId);
    }

    public String getDefaultPrompt() {
        return loadPromptFile("default_user_prompt", "de");
    }

    /**
     * Opens a conversation that produces one question per turn.
     *
     * <p>The first user message carries the station's instructions and every existing title, so
     * the model has the whole context from the start and each later turn only asks for one more.
     *
     * @throws IllegalArgumentException when the provider is unknown or no API key is available
     */
    public ChatSession createQuestionSession(
            int stationId,
            int accountId,
            String provider,
            String transientKey,
            String model,
            QuizQuestionType quizQuestionType,
            String userPrompt,
            String locale,
            String categoryName,
            String categoryDescription,
            List<String> existingTitles) {
        AiVendor vendor = requireVendor(provider);
        String apiKey = resolveApiKey(stationId, accountId, provider, transientKey);
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("No API key available");
        String resolvedModel = resolveModel(stationId, vendor, model);
        String effectiveLocale = locale != null ? locale : "de";

        String systemPrompt =
                loadPromptFile(quizQuestionType.promptFile(), effectiveLocale).replace("{count}", "1");
        systemPrompt +=
                "\n\nWICHTIG: Erstelle KEINE Fragen die identisch oder ähnlich zu bereits genannten Fragen sind.";

        var session = new ChatSession(vendor, apiKey, resolvedModel, systemPrompt);

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

    /**
     * Runs one turn of the conversation and, when it produced a usable question, queues the request
     * for the next one. The model already holds the whole context, so that request is short.
     */
    public List<GeneratedQuestion> generateNextQuestion(ChatSession session, QuizQuestionType quizQuestionType) {
        try {
            String text = chatWithSession(session);
            session.addAssistantMessage(text);
            var parsed = parseAndValidate(text, quizQuestionType);
            if (!parsed.isEmpty()) {
                session.addUserMessage("Erstelle eine weitere einzigartige Frage. Wiederhole keine der bisherigen.");
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Session-based generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Generates wrong answers for a question.
     *
     * @throws IllegalArgumentException when the provider is unknown or no API key is available
     */
    public List<String> generate(
            int stationId,
            int accountId,
            String provider,
            String transientKey,
            String model,
            String question,
            String correctAnswer,
            int count) {
        AiVendor vendor = requireVendor(provider);
        String apiKey = resolveApiKey(stationId, accountId, provider, transientKey);
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("No API key available");
        String resolvedModel = resolveModel(stationId, vendor, model);
        String systemPrompt = loadPromptFile("wrong_answers", "de").replace("{count}", String.valueOf(count));
        String userPrompt = getPrompt(stationId);
        String fullUserMessage = userPrompt + "\n\nFrage: " + question + "\nRichtige Antwort: " + correctAnswer;

        try {
            String text = chat(vendor, apiKey, resolvedModel, systemPrompt, fullUserMessage);
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

    /**
     * Lists the chat models the key can use, or nothing for a provider this service does not know.
     *
     * @throws IllegalArgumentException when no API key is available
     */
    public List<ModelInfo> fetchModels(int stationId, int accountId, String provider, String transientKey) {
        String apiKey = resolveApiKey(stationId, accountId, provider, transientKey);
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("No API key available");
        var vendor = AiVendor.fromKey(provider);
        if (vendor.isEmpty()) return List.of();
        try {
            return switch (vendor.get()) {
                case OPENAI -> fetchOpenAiModels(apiKey);
                case GEMINI -> fetchGeminiModels(apiKey);
                case CLAUDE -> fetchClaudeModels(apiKey);
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
            } catch (IOException e) {
                log.warn("Prompt template {} could not be read, falling back to German", path, e);
            }
            Path fallback = PROMPTS_DIR.resolve("de").resolve(name + ".txt");
            try {
                if (Files.exists(fallback)) return Files.readString(fallback);
            } catch (IOException e) {
                log.warn("German fallback prompt template {} could not be read either", fallback, e);
            }
            log.warn("Prompt template not found: {}", path);
            return "";
        });
    }

    /**
     * The key a call is made with: one sent along with the request, then the caller's own, then the
     * station's.
     *
     * <p>A key in the request is how a page from before keys moved to the server still works; a
     * current page sends none.
     */
    // TODO: stop taking a key in the request once no page from before the switch can be open
    private String resolveApiKey(int stationId, int accountId, String provider, String transientKey) {
        if (transientKey != null && !transientKey.isBlank()) return transientKey;
        return credentials
                .keyFor(accountId, provider)
                .or(() -> providerRepository.findByProvider(stationId, provider).map(StationAiProvider::apiKey))
                .orElse(null);
    }

    private static AiVendor requireVendor(String provider) {
        return AiVendor.fromKey(provider)
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + provider));
    }

    private String resolveModel(int stationId, AiVendor vendor, String requestModel) {
        if (requestModel != null && !requestModel.isBlank()) return requestModel;
        return providerRepository
                .findByProvider(stationId, vendor.key())
                .map(StationAiProvider::model)
                .filter(m -> !m.isBlank())
                .orElse(vendor.defaultModel());
    }

    private String chat(AiVendor vendor, String apiKey, String model, String systemPrompt, String userMessage) {
        return switch (vendor) {
            case OPENAI -> chatOpenAi(apiKey, model, systemPrompt, userMessage);
            case GEMINI -> chatGemini(apiKey, model, systemPrompt, userMessage);
            case CLAUDE -> chatClaude(apiKey, model, systemPrompt, userMessage);
        };
    }

    private String chatWithSession(ChatSession session) {
        return switch (session.vendor()) {
            case OPENAI -> chatOpenAiSession(session);
            case GEMINI -> chatGeminiSession(session);
            case CLAUDE -> chatClaudeSession(session);
        };
    }

    private String chatOpenAiSession(ChatSession session) {
        OpenAIClient client = clients.openAi(session.apiKey());
        try {
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
        } finally {
            client.close();
        }
    }

    /**
     * The Gemini SDK has no multi-turn conversation of its own here, so the turns so far are
     * written into one message.
     */
    private String chatGeminiSession(ChatSession session) {
        var fullMessage = new StringBuilder();
        for (var msg : session.messages()) {
            fullMessage
                    .append(msg.role().equals("user") ? "User: " : "Assistant: ")
                    .append(msg.content())
                    .append("\n\n");
        }
        return chatGemini(session.apiKey(), session.model(), session.systemPrompt(), fullMessage.toString());
    }

    private String chatClaudeSession(ChatSession session) {
        AnthropicClient client = clients.anthropic(session.apiKey());
        try {
            var builder = MessageCreateParams.builder()
                    .model(session.model())
                    .maxTokens(2048L)
                    .system(session.systemPrompt());
            for (var msg : session.messages()) {
                if ("user".equals(msg.role())) builder.addUserMessage(msg.content());
                else if ("assistant".equals(msg.role())) builder.addAssistantMessage(msg.content());
            }
            return firstText(client.messages().create(builder.build()).content());
        } finally {
            client.close();
        }
    }

    private static String firstText(List<ContentBlock> content) {
        return content.stream()
                .filter(ContentBlock::isText)
                .map(b -> b.asText().text())
                .findFirst()
                .orElse("");
    }

    /**
     * Reads the model's answer as a JSON array of questions, dropping a surrounding markdown fence
     * first, and keeps the questions whose config maps onto the typed config of the question type.
     */
    private List<GeneratedQuestion> parseAndValidate(String text, QuizQuestionType quizQuestionType) throws Exception {
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n') + 1;
            int end = text.lastIndexOf("```");
            if (end > start) text = text.substring(start, end).trim();
        }

        var array = Json.MAPPER.readTree(text);
        if (!array.isArray()) throw new RuntimeException("AI did not return a JSON array");

        var results = new ArrayList<GeneratedQuestion>();
        for (var item : array) {
            String title = item.has("title") ? item.get("title").asString() : "";
            if (title.isBlank()) continue;
            if (!item.has("config")) continue;

            var configNode = item.get("config");
            String configJson = Json.MAPPER.writeValueAsString(configNode);

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
                    var cfg = Json.MAPPER.readValue(configJson, QuestionConfig.MultipleChoice.class);
                    yield cfg.options() != null
                            && cfg.options().size() >= 2
                            && cfg.options().stream().anyMatch(QuestionConfig.MultipleChoice.Option::correct)
                            && cfg.options().stream()
                                    .allMatch(o -> o.text() != null && !o.text().isBlank());
                }
                case TRUE_FALSE -> {
                    Json.MAPPER.readValue(configJson, QuestionConfig.TrueFalse.class);
                    yield true;
                }
                case FREE_ANSWER -> {
                    var cfg = Json.MAPPER.readValue(configJson, QuestionConfig.FreeAnswer.class);
                    yield cfg.lines() > 0;
                }
                case FILL_IN_THE_BLANK -> {
                    var cfg = Json.MAPPER.readValue(configJson, QuestionConfig.FillInTheBlank.class);
                    yield cfg.text() != null
                            && !cfg.text().isBlank()
                            && cfg.answers() != null
                            && !cfg.answers().isEmpty();
                }
                case CONNECT -> {
                    var cfg = Json.MAPPER.readValue(configJson, QuestionConfig.Connect.class);
                    yield cfg.pairs() != null
                            && cfg.pairs().size() >= 2
                            && cfg.pairs().stream()
                                    .allMatch(p -> p.left() != null
                                            && !p.left().isBlank()
                                            && p.right() != null
                                            && !p.right().isBlank());
                }
                case ORDERING -> {
                    var cfg = Json.MAPPER.readValue(configJson, QuestionConfig.Ordering.class);
                    yield cfg.items() != null
                            && cfg.items().size() >= 2
                            && cfg.items().stream().allMatch(i -> i != null && !i.isBlank());
                }
                case IMAGE_TEXT -> true;
                case ENUMERATION -> {
                    var cfg = Json.MAPPER.readTree(configJson);
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
        OpenAIClient client = clients.openAi(apiKey);
        try {
            var params = ChatCompletionCreateParams.builder()
                    .model(model)
                    .addSystemMessage(systemPrompt)
                    .addUserMessage(userMessage)
                    .temperature(0.8)
                    .build();
            var completion = client.chat().completions().create(params);
            return completion.choices().getFirst().message().content().orElse("");
        } finally {
            client.close();
        }
    }

    private List<ModelInfo> fetchOpenAiModels(String apiKey) {
        OpenAIClient client = clients.openAi(apiKey);
        try {
            var result = new ArrayList<ModelInfo>();
            for (var m : client.models().list().data()) {
                if (isChatModel(m.id())) {
                    result.add(new ModelInfo(m.id(), m.id()));
                }
            }
            result.sort(Comparator.comparing(ModelInfo::name));
            return result;
        } finally {
            client.close();
        }
    }

    private String chatGemini(String apiKey, String model, String systemPrompt, String userMessage) {
        try (Client client = clients.gemini(apiKey)) {
            var config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                    .build();
            var response = client.models.generateContent(model, userMessage, config);
            return response.text();
        }
    }

    private List<ModelInfo> fetchGeminiModels(String apiKey) {
        try (Client client = clients.gemini(apiKey)) {
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
        AnthropicClient client = clients.anthropic(apiKey);
        try {
            var params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(2048L)
                    .system(systemPrompt)
                    .addUserMessage(userMessage)
                    .build();
            return firstText(client.messages().create(params).content());
        } finally {
            client.close();
        }
    }

    private List<ModelInfo> fetchClaudeModels(String apiKey) {
        AnthropicClient client = clients.anthropic(apiKey);
        try {
            var result = new ArrayList<ModelInfo>();
            for (var m : client.models().list().data()) {
                result.add(new ModelInfo(m.id(), m.displayName()));
            }
            result.sort(Comparator.comparing(ModelInfo::name));
            return result;
        } finally {
            client.close();
        }
    }

    public record ChatMessage(String role, String content) {}

    public static class ChatSession {
        private final AiVendor vendor;
        private final String apiKey;
        private final String model;
        private final String systemPrompt;
        private final List<ChatMessage> messages = new ArrayList<>();

        ChatSession(AiVendor vendor, String apiKey, String model, String systemPrompt) {
            this.vendor = vendor;
            this.apiKey = apiKey;
            this.model = model;
            this.systemPrompt = systemPrompt;
        }

        public List<ChatMessage> messages() {
            return messages;
        }

        public AiVendor vendor() {
            return vendor;
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

    public record ModelInfo(String id, String name) {}
}
