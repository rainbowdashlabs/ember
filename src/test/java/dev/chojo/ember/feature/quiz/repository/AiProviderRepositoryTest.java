/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiProviderRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("AiProviderStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static String storedProvider(AiVendor vendor) {
        return query(
                        "SELECT provider FROM station_ai_provider WHERE station_id = :station_id AND provider = :provider;")
                .single(call().bind("station_id", station.id()).bind("provider", vendor.key()))
                .map(row -> row.getString("provider"))
                .first()
                .orElseThrow();
    }

    @Test
    @Order(1)
    void upsertAndFindByProvider() {
        aiProviderRepo.upsert(station.id(), AiVendor.OPENAI, "sk-test-key", "gpt-4");

        var found = aiProviderRepo.findByProvider(station.id(), AiVendor.OPENAI);
        assertTrue(found.isPresent());
        assertEquals(AiVendor.OPENAI, found.get().provider());
        assertEquals("gpt-4", found.get().model());
        assertEquals("openai", storedProvider(AiVendor.OPENAI), "stored under the key rows always had");
    }

    @Test
    @Order(2)
    void findByStation() {
        var providers = aiProviderRepo.findByStation(station.id());
        assertFalse(providers.isEmpty());
        assertTrue(providers.stream().anyMatch(p -> p.provider() == AiVendor.OPENAI));
    }

    @Test
    @Order(3)
    void upsertUpdatesExisting() {
        aiProviderRepo.upsert(station.id(), AiVendor.OPENAI, "sk-new-key", "gpt-4o");
        var found = aiProviderRepo.findByProvider(station.id(), AiVendor.OPENAI).orElseThrow();
        assertEquals("gpt-4o", found.model());
    }

    @Test
    @Order(4)
    void findByProviderNotFound() {
        assertTrue(aiProviderRepo.findByProvider(station.id(), AiVendor.GEMINI).isEmpty());
    }

    @Test
    @Order(5)
    void getAndSetPrompt() {
        aiProviderRepo.setPrompt(station.id(), "Generate quiz questions about fire safety.");
        var prompt = aiProviderRepo.getPrompt(station.id());
        assertTrue(prompt.isPresent());
        assertEquals("Generate quiz questions about fire safety.", prompt.get());
    }

    @Test
    @Order(6)
    void setPromptOverwrites() {
        aiProviderRepo.setPrompt(station.id(), "Updated prompt.");
        assertEquals("Updated prompt.", aiProviderRepo.getPrompt(station.id()).orElseThrow());
    }

    @Test
    @Order(7)
    void upsertMultipleProviders() {
        aiProviderRepo.upsert(station.id(), AiVendor.CLAUDE, "claude-key", "claude-3");
        var providers = aiProviderRepo.findByStation(station.id());
        assertTrue(providers.size() >= 2);
    }

    @Test
    @Order(8)
    void plaintextKeysAreFoundAndReplacedOnlyWhileUnchanged() {
        aiProviderRepo.upsert(station.id(), AiVendor.GEMINI, "enc:v1:already-sealed", null);
        var gemini =
                aiProviderRepo.findByProvider(station.id(), AiVendor.GEMINI).orElseThrow();
        var claude =
                aiProviderRepo.findByProvider(station.id(), AiVendor.CLAUDE).orElseThrow();

        var plaintext = aiProviderRepo.findWithoutPrefix("enc:v1:");
        assertTrue(plaintext.stream().anyMatch(p -> p.id() == claude.id()));
        assertTrue(plaintext.stream().noneMatch(p -> p.id() == gemini.id()));

        assertFalse(aiProviderRepo.replaceKeyIfUnchanged(claude.id(), "not-what-is-stored", "enc:v1:x"));
        assertTrue(aiProviderRepo.replaceKeyIfUnchanged(claude.id(), "claude-key", "enc:v1:sealed"));
        assertEquals(
                "enc:v1:sealed",
                aiProviderRepo
                        .findByProvider(station.id(), AiVendor.CLAUDE)
                        .orElseThrow()
                        .apiKey());
    }

    @Test
    @Order(9)
    void aStoredProviderThisInstanceDoesNotKnowIsLeftOut() {
        query("INSERT INTO station_ai_provider(station_id, provider, api_key) VALUES (:station_id, 'mystery', 'k');")
                .single(call().bind("station_id", station.id()))
                .insert();

        var providers = aiProviderRepo.findByStation(station.id());

        assertEquals(3, providers.size());
        assertEquals(
                List.of(AiVendor.OPENAI),
                aiProviderRepo.findWithoutPrefix("enc:v1:").stream()
                        .filter(p -> p.stationId() == station.id())
                        .map(StationAiProvider::provider)
                        .toList());
    }

    @Test
    @Order(99)
    void delete() {
        aiProviderRepo.delete(station.id(), AiVendor.OPENAI);
        assertTrue(aiProviderRepo.findByProvider(station.id(), AiVendor.OPENAI).isEmpty());

        aiProviderRepo.delete(station.id(), AiVendor.CLAUDE);
        assertTrue(aiProviderRepo.findByProvider(station.id(), AiVendor.CLAUDE).isEmpty());
    }
}
