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
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every vendor client is built for one call and closed when that call ends, whether it answered
 * or failed, and the provider key picks the vendor.
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
        when(credentials.stationKey(1, "claude")).thenReturn(Optional.of("station"));
        when(credentials.keyFor(7, "claude")).thenReturn(Optional.of("personal"));
        when(clients.anthropic(anyString())).thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.generate(1, 7, "claude", null, "Q", "A", 1));
        assertThrows(RuntimeException.class, () -> service.generate(1, 8, "claude", null, "Q", "A", 1));

        verify(clients).anthropic("personal");
        verify(clients).anthropic("station");
    }

    @Test
    void aTypedKeyIsTriedForTheModelListBeforeTheStoredOne() {
        when(credentials.keyFor(7, "claude")).thenReturn(Optional.of("personal"));
        when(clients.anthropic(anyString())).thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.fetchModels(1, 7, "claude", "typed"));
        assertThrows(RuntimeException.class, () -> service.fetchModels(1, 7, "claude", " "));

        verify(clients).anthropic("typed");
        verify(clients).anthropic("personal");
    }

    @Test
    void withoutAnyKeyNothingIsCalled() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(1, 7, "claude", null, "Q", "A", 1));
        assertThrows(IllegalArgumentException.class, () -> service.fetchModels(1, 7, "claude", null));

        verifyNoInteractions(clients);
    }

    @Test
    void aStationKeyIsSavedThroughTheCredentialsSoItIsEncrypted() {
        service.saveProvider(1, "openai", "sk-station", "gpt-4o");

        verify(credentials).saveStationKey(1, "openai", "sk-station", "gpt-4o");
        verifyNoInteractions(stations);
    }

    @Test
    void anOpenAiClientIsClosedWhenTheCallFails() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(credentials.keyFor(1, "openai")).thenReturn(Optional.of("key"));
        when(clients.openAi("key")).thenReturn(client);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.generate(1, 1, "openai", null, "Q", "A", 3));

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
        when(credentials.keyFor(1, "claude")).thenReturn(Optional.of("key"));
        when(clients.anthropic("key")).thenReturn(client);

        assertEquals(List.of("first", "second"), service.generate(1, 1, "claude", null, "Q", "A", 2));

        verify(client).close();
    }

    @Test
    void anAnthropicClientIsClosedWhenListingModelsFails() {
        AnthropicClient client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        when(clients.anthropic("key")).thenReturn(client);
        when(client.models().list()).thenThrow(new IllegalStateException("offline"));

        assertThrows(RuntimeException.class, () -> service.fetchModels(1, 1, "claude", "key"));

        verify(client).close();
    }

    @Test
    void aSessionTurnClosesItsClient() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(credentials.stationKey(1, "openai")).thenReturn(Optional.of("key"));
        when(clients.openAi("key")).thenReturn(client);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new IllegalStateException("offline"));
        var session = service.createQuestionSession(
                1, 1, "openai", null, QuizQuestionType.TRUE_FALSE, "prompt", "de", null, null, List.of());

        assertTrue(service.generateNextQuestion(session, QuizQuestionType.TRUE_FALSE)
                .isEmpty());

        verify(client).close();
    }

    @Test
    void anUnknownProviderBuildsNoClient() {
        when(credentials.keyFor(1, "mystery")).thenReturn(Optional.of("key"));

        assertThrows(IllegalArgumentException.class, () -> service.generate(1, 1, "mystery", null, "Q", "A", 1));
        assertTrue(service.fetchModels(1, 1, "mystery", "key").isEmpty());

        verifyNoInteractions(clients);
    }
}
