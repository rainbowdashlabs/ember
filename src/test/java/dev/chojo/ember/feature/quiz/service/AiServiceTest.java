/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.models.Model;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import dev.chojo.ember.feature.quiz.service.AiService.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every vendor client is built for one call and closed when that call ends, whether it answered
 * or failed, and the provider picks the vendor client.
 */
class AiServiceTest {

    private AiClientFactory clients;
    private AiService service;
    private AiProviderRepository stations;
    private AiCredentialService credentials;

    @BeforeEach
    void setUp() {
        clients = mock(AiClientFactory.class);
        stations = mock(AiProviderRepository.class);
        credentials = mock(AiCredentialService.class);
        service = new AiService(stations, clients, credentials);
    }

    @Test
    void theKeyComesFromThePersonThenTheStation() {
        when(credentials.stationKey(1, AiVendor.CLAUDE)).thenReturn(Optional.of("station"));
        when(credentials.keyFor(7, AiVendor.CLAUDE)).thenReturn(Optional.of("personal"));
        when(clients.anthropic(anyString())).thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.generate(1, 7, AiVendor.CLAUDE, null, "Q", "A", 1));
        assertThrows(RuntimeException.class, () -> service.generate(1, 8, AiVendor.CLAUDE, null, "Q", "A", 1));

        verify(clients).anthropic("personal");
        verify(clients).anthropic("station");
    }

    @Test
    void aTypedKeyIsTriedForTheModelListBeforeTheStoredOne() {
        when(credentials.keyFor(7, AiVendor.CLAUDE)).thenReturn(Optional.of("personal"));
        when(clients.anthropic(anyString())).thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.fetchModels(1, 7, AiVendor.CLAUDE, "typed"));
        assertThrows(RuntimeException.class, () -> service.fetchModels(1, 7, AiVendor.CLAUDE, " "));

        verify(clients).anthropic("typed");
        verify(clients).anthropic("personal");
    }

    @Test
    void withoutAnyKeyNothingIsCalled() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(1, 7, AiVendor.CLAUDE, null, "Q", "A", 1));
        assertThrows(IllegalArgumentException.class, () -> service.fetchModels(1, 7, AiVendor.CLAUDE, null));

        verifyNoInteractions(clients);
    }

    @Test
    void aStationKeyIsSavedThroughTheCredentialsSoItIsEncrypted() {
        service.saveProvider(1, AiVendor.OPENAI, "sk-station", "gpt-4o");

        verify(credentials).saveStationKey(1, AiVendor.OPENAI, "sk-station", "gpt-4o");
        verifyNoInteractions(stations);
    }

    @Test
    void anOpenAiClientIsClosedWhenTheCallFails() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(credentials.keyFor(1, AiVendor.OPENAI)).thenReturn(Optional.of("key"));
        when(clients.openAi(AiVendor.OPENAI, "key")).thenReturn(client);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.generate(1, 1, AiVendor.OPENAI, null, "Q", "A", 3));

        verify(client).close();
    }

    @Test
    void anAnthropicClientIsClosedAfterAnAnswer() {
        AnthropicClient client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        Message message = mock(Message.class);
        ContentBlock block = mock(ContentBlock.class, RETURNS_DEEP_STUBS);
        when(block.isText()).thenReturn(true);
        when(block.asText().text()).thenReturn("first\nsecond\n\nthird");
        when(message.content()).thenReturn(List.of(block));
        when(client.messages().create(any(MessageCreateParams.class))).thenReturn(message);
        when(credentials.keyFor(1, AiVendor.CLAUDE)).thenReturn(Optional.of("key"));
        when(clients.anthropic("key")).thenReturn(client);

        assertEquals(List.of("first", "second"), service.generate(1, 1, AiVendor.CLAUDE, null, "Q", "A", 2));

        verify(client).close();
    }

    @Test
    void anAnthropicClientIsClosedWhenListingModelsFails() {
        AnthropicClient client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        when(clients.anthropic("key")).thenReturn(client);
        when(client.models().list()).thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.fetchModels(1, 1, AiVendor.CLAUDE, "key"));

        verify(client).close();
    }

    @Test
    void aSessionTurnClosesItsClient() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(credentials.stationKey(1, AiVendor.OPENAI)).thenReturn(Optional.of("key"));
        when(clients.openAi(AiVendor.OPENAI, "key")).thenReturn(client);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new IllegalStateException("offline"));
        var session = service.createQuestionSession(
                1, 1, AiVendor.OPENAI, null, QuizQuestionType.TRUE_FALSE, "prompt", "de", null, null, List.of());

        assertTrue(service.generateNextQuestion(session, QuizQuestionType.TRUE_FALSE)
                .isEmpty());

        verify(client).close();
    }

    @ParameterizedTest
    @EnumSource(
            value = AiVendor.class,
            names = {"DEEPSEEK", "MISTRAL"})
    void anOpenAiCompatibleVendorAnswersThroughTheOpenAiPathWithItsDefaultModel(AiVendor vendor) {
        OpenAIClient client = answering("first\nsecond\nthird");
        when(credentials.keyFor(1, vendor)).thenReturn(Optional.of("v-key"));
        when(clients.openAi(vendor, "v-key")).thenReturn(client);

        assertEquals(List.of("first", "second"), service.generate(1, 1, vendor, null, "Q", "A", 2));

        var sent = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions()).create(sent.capture());
        assertEquals(vendor.defaultModel(), sent.getValue().model().asString());
        assertOnlyWhatEveryOpenAiCompatibleVendorTakes(sent.getValue());
        verify(client).close();
    }

    @ParameterizedTest
    @EnumSource(
            value = AiVendor.class,
            names = {"DEEPSEEK", "MISTRAL"})
    void anOpenAiCompatibleSessionWritesQuestionsTurnByTurn(AiVendor vendor) {
        OpenAIClient client = answering("""
                ```json
                [{"title":"Wasser löscht Fettbrände","config":{"correctAnswer":false}}]
                ```""");
        when(credentials.stationKey(1, vendor)).thenReturn(Optional.of("v-key"));
        when(clients.openAi(vendor, "v-key")).thenReturn(client);
        var session = service.createQuestionSession(
                1, 1, vendor, "chosen-model", QuizQuestionType.TRUE_FALSE, "Brandschutz", "de", null, null, List.of());

        var first = service.generateNextQuestion(session, QuizQuestionType.TRUE_FALSE);
        service.generateNextQuestion(session, QuizQuestionType.TRUE_FALSE);

        assertEquals("Wasser löscht Fettbrände", first.getFirst().title());
        var sent = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions(), times(2)).create(sent.capture());
        assertEquals("chosen-model", sent.getValue().model().asString());
        assertEquals(4, sent.getValue().messages().size(), "system, first ask, the answer, the next ask");
        assertOnlyWhatEveryOpenAiCompatibleVendorTakes(sent.getValue());
        verify(client, times(2)).close();
    }

    @Test
    void deepSeekListsItsOwnChatModels() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(clients.openAi(AiVendor.DEEPSEEK, "ds-key")).thenReturn(client);
        var listed = List.of(model("deepseek-reasoner"), model("deepseek-chat"), model("text-embedding-x"));
        when(client.models().list().data()).thenReturn(listed);

        assertEquals(
                List.of(
                        new ModelInfo("deepseek-chat", "deepseek-chat"),
                        new ModelInfo("deepseek-reasoner", "deepseek-reasoner")),
                service.fetchModels(1, 1, AiVendor.DEEPSEEK, "ds-key"));
        verify(client).close();
    }

    @Test
    void mistralListsItsChatModelsWithoutEmbeddingsModerationOrOcr() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(clients.openAi(AiVendor.MISTRAL, "m-key")).thenReturn(client);
        var listed = List.of(
                model("mistral-small-latest"),
                model("mistral-embed"),
                model("mistral-moderation-latest"),
                model("mistral-ocr-latest"),
                model("pixtral-large-latest"));
        when(client.models().list().data()).thenReturn(listed);

        assertEquals(
                List.of(
                        new ModelInfo("mistral-small-latest", "mistral-small-latest"),
                        new ModelInfo("pixtral-large-latest", "pixtral-large-latest")),
                service.fetchModels(1, 1, AiVendor.MISTRAL, "m-key"));
        verify(client).close();
    }

    @Test
    void openAiStillOffersOnlyItsChatFamilies() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(clients.openAi(AiVendor.OPENAI, "key")).thenReturn(client);
        var listed = List.of(model("gpt-4o-mini"), model("deepseek-chat"));
        when(client.models().list().data()).thenReturn(listed);

        assertEquals(
                List.of(new ModelInfo("gpt-4o-mini", "gpt-4o-mini")),
                service.fetchModels(1, 1, AiVendor.OPENAI, "key"));
    }

    /**
     * Structured output, tool use and a token limit are what OpenAI-compatible vendors disagree on,
     * so the OpenAI path sends none of them and asks for the JSON in the prompt instead.
     */
    private static void assertOnlyWhatEveryOpenAiCompatibleVendorTakes(ChatCompletionCreateParams params) {
        assertTrue(params.responseFormat().isEmpty());
        assertTrue(params.tools().isEmpty());
        assertTrue(params.maxTokens().isEmpty());
        assertTrue(params.maxCompletionTokens().isEmpty());
    }

    private static OpenAIClient answering(String text) {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        ChatCompletion completion = mock(ChatCompletion.class);
        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class, RETURNS_DEEP_STUBS);
        when(choice.message().content()).thenReturn(Optional.of(text));
        when(completion.choices()).thenReturn(List.of(choice));
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenReturn(completion);
        return client;
    }

    private static Model model(String id) {
        Model model = mock(Model.class);
        when(model.id()).thenReturn(id);
        return model;
    }
}
