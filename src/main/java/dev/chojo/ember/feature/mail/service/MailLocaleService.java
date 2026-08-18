/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Which language a system mail is written in.
 *
 * <p>An account is written to in the language of the station it was created from, because that is
 * the language the people around it speak. An account that belongs to no station - somebody who
 * signed up on their own, the administrator laid down at first start - is written to in the
 * language the instance has chosen for exactly this case.
 *
 * <p>The answer is worked out on every send rather than stored on the account, so changing the
 * setting, or a station's language, takes effect for the next mail without touching any account.
 */
@Singleton
public class MailLocaleService {

    private final AccountRepository accountRepository;
    private final ApplicationSettingRepository settingRepository;

    @Inject
    public MailLocaleService(AccountRepository accountRepository, ApplicationSettingRepository settingRepository) {
        this.accountRepository = accountRepository;
        this.settingRepository = settingRepository;
    }

    /**
     * The language to write to this account in.
     *
     * @param accountId the recipient
     * @return the short ISO 639 code, suitable for mail template lookup
     */
    public String forAccount(int accountId) {
        return accountRepository.findStationLanguage(accountId).orElseGet(settingRepository::defaultMailLocale);
    }
}
