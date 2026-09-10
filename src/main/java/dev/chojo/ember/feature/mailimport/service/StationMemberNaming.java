/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * The station's members, as names a subject line might contain.
 *
 * <p>Former members are left out. A rule reading a name out of a subject is filing a document about
 * somebody who is in the station now, and offering people who have left would make an ambiguity out of a
 * name that is only shared with somebody who is gone.
 */
@Singleton
public class StationMemberNaming implements MailFilingService.MemberNaming {

    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public StationMemberNaming(StationMemberRepository memberRepository, AccountRepository accountRepository) {
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public List<SubjectMemberMatch.Candidate> candidates(int stationId) {
        return memberRepository.findByStation(stationId).stream()
                .map(member -> new SubjectMemberMatch.Candidate(member.id(), nameOf(member.accountId())))
                .filter(candidate -> !candidate.fullName().isBlank())
                .toList();
    }

    private String nameOf(Integer accountId) {
        if (accountId == null) return "";
        return accountRepository
                .findById(accountId)
                .map(account -> (account.firstName() + " " + account.lastName()).trim())
                .orElse("");
    }
}
