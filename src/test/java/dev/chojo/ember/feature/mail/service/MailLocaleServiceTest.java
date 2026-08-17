/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which language a system mail is written in.
 *
 * <p>An account is written to in the language of the station it was created from. The accounts that
 * belong to no station - somebody who signed up on their own, the administrator laid down at first
 * start - used to be answered in English whatever the instance wanted, which is what the
 * instance-wide default settles.
 */
class MailLocaleServiceTest extends RepositoryTestBase {

    private static final ApplicationSettingRepository settings = new ApplicationSettingRepository();
    private static MailLocaleService mailLocale;

    private static Station germanStation;
    private static int accountWithoutStation;
    private static int accountFromGermanStation;

    @BeforeAll
    static void setup() {
        mailLocale = new MailLocaleService(accountRepo, settings);
        germanStation = stationRepo.create("Mail Locale Station");
        stationRepo.updateLocale(germanStation.id(), "de-DE");

        accountWithoutStation = accountRepo
                .create("no-station@maillocale.test", "Ohne", "Wache")
                .id();
        accountFromGermanStation = accountRepo
                .create("from-station@maillocale.test", "Aus", "Wache", germanStation.id())
                .id();
    }

    @AfterAll
    static void cleanup() {
        accountRepo.delete(accountWithoutStation);
        accountRepo.delete(accountFromGermanStation);
        stationRepo.delete(germanStation.id());
        settings.set(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE, "");
    }

    @Test
    void anInstanceThatNeverChoseWritesEnglish() {
        settings.set(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE, "");

        assertEquals("en", mailLocale.forAccount(accountWithoutStation));
    }

    @Test
    void theInstanceDefaultDecidesForAnAccountWithoutAStation() {
        settings.set(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE, "de");

        assertEquals("de", mailLocale.forAccount(accountWithoutStation));
    }

    /**
     * The station keeps the last word: a member of a German station is written to in German even
     * where the instance answers everyone else in English.
     */
    @Test
    void theStationOfTheAccountStillWins() {
        settings.set(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE, "en");

        assertEquals("de", mailLocale.forAccount(accountFromGermanStation));
    }
}
