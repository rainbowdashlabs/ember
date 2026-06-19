/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class BackupCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BLOCK_SIZE = 4;
    private static final int BLOCKS = 3;

    private final int codeCount;

    @Inject
    public BackupCodeService(TwoFactorSettings settings) {
        this.codeCount = settings.backupCodes().count();
    }

    public List<String> generateCodes() {
        var codes = new ArrayList<String>(codeCount);
        for (int i = 0; i < codeCount; i++) {
            codes.add(generateCode());
        }
        return codes;
    }

    public String hashCode(String code) {
        String normalized = code.replace("-", "").toUpperCase();
        return BCrypt.withDefaults().hashToString(10, normalized.toCharArray());
    }

    public boolean verifyCode(String code, String hash) {
        String normalized = code.replace("-", "").toUpperCase();
        return BCrypt.verifyer().verify(normalized.toCharArray(), hash).verified;
    }

    private String generateCode() {
        var sb = new StringBuilder(BLOCK_SIZE * BLOCKS + BLOCKS - 1);
        for (int block = 0; block < BLOCKS; block++) {
            if (block > 0) sb.append('-');
            for (int i = 0; i < BLOCK_SIZE; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }
        return sb.toString();
    }
}
