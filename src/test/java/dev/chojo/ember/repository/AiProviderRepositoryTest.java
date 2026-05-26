/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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

    @Test
    @Order(1)
    void upsertAndFindByProvider() {
        aiProviderRepo.upsert(station.id(), "openai", "sk-test-key", "gpt-4");

        var found = aiProviderRepo.findByProvider(station.id(), "openai");
        assertTrue(found.isPresent());
        assertEquals("openai", found.get().provider());
        assertEquals("gpt-4", found.get().model());
    }

    @Test
    @Order(2)
    void findByStation() {
        var providers = aiProviderRepo.findByStation(station.id());
        assertFalse(providers.isEmpty());
        assertTrue(providers.stream().anyMatch(p -> "openai".equals(p.provider())));
    }

    @Test
    @Order(3)
    void upsertUpdatesExisting() {
        aiProviderRepo.upsert(station.id(), "openai", "sk-new-key", "gpt-4o");
        var found = aiProviderRepo.findByProvider(station.id(), "openai").orElseThrow();
        assertEquals("gpt-4o", found.model());
    }

    @Test
    @Order(4)
    void findByProviderNotFound() {
        assertTrue(aiProviderRepo.findByProvider(station.id(), "nonexistent").isEmpty());
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
        aiProviderRepo.upsert(station.id(), "anthropic", "claude-key", "claude-3");
        var providers = aiProviderRepo.findByStation(station.id());
        assertTrue(providers.size() >= 2);
    }

    @Test
    @Order(99)
    void delete() {
        aiProviderRepo.delete(station.id(), "openai");
        assertTrue(aiProviderRepo.findByProvider(station.id(), "openai").isEmpty());

        aiProviderRepo.delete(station.id(), "anthropic");
        assertTrue(aiProviderRepo.findByProvider(station.id(), "anthropic").isEmpty());
    }
}
