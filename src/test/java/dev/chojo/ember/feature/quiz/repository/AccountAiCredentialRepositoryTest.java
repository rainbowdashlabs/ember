/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One key per account, written over on every save and gone with the account. */
class AccountAiCredentialRepositoryTest extends RepositoryTestBase {
    private static final AccountAiCredentialRepository repository = new AccountAiCredentialRepository();
    private static Account account;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("ai-credential@test.com", "Ki", "Schlüssel");
    }

    @AfterAll
    static void cleanup() {
        accountRepo.delete(account.id());
    }

    @Test
    void aSaveIsReadBackAndASecondOneReplacesIt() {
        repository.save(account.id(), AiVendor.OPENAI, "gpt-4o", "enc:v1:first");
        var first = repository.find(account.id()).orElseThrow();
        assertEquals(AiVendor.OPENAI, first.provider());
        assertEquals("gpt-4o", first.model());
        assertEquals("enc:v1:first", first.sealedKey());
        assertNotNull(first.updatedAt());

        repository.save(account.id(), AiVendor.CLAUDE, null, "enc:v1:second");
        var second = repository.find(account.id()).orElseThrow();
        assertEquals(AiVendor.CLAUDE, second.provider());
        assertNull(second.model());
        assertEquals("enc:v1:second", second.sealedKey());

        assertTrue(repository.delete(account.id()));
        assertTrue(repository.find(account.id()).isEmpty());
        assertFalse(repository.delete(account.id()));
    }

    @Test
    void theKeyGoesWithTheAccount() {
        var leaving = accountRepo.create("ai-credential-leaving@test.com", "Ki", "Weg");
        repository.save(leaving.id(), AiVendor.GEMINI, null, "enc:v1:gone");

        accountRepo.delete(leaving.id());

        assertTrue(repository.find(leaving.id()).isEmpty());
    }

    @Test
    void aKeyForAProviderThisInstanceDoesNotKnowReadsAsNone() {
        var stranger = accountRepo.create("ai-credential-unknown@test.com", "Ki", "Fremd");
        query("""
                INSERT INTO account_ai_credential(account_id, provider, api_key)
                VALUES (:account_id, 'mystery', 'enc:v1:x');""").single(call().bind("account_id", stranger.id())).insert();

        try {
            assertTrue(repository.find(stranger.id()).isEmpty());
        } finally {
            accountRepo.delete(stranger.id());
        }
    }
}
