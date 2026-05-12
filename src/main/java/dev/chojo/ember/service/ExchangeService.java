/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.ExchangeLog;
import dev.chojo.ember.entity.ExchangeRequest;
import dev.chojo.ember.entity.ExchangeStatus;
import dev.chojo.ember.repository.ExchangeRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class ExchangeService {
    private final ExchangeRepository exchangeRepository;

    @Inject
    public ExchangeService(ExchangeRepository exchangeRepository) {
        this.exchangeRepository = exchangeRepository;
    }

    public ExchangeRequest create(int stationId, int memberId, Integer itemId, int inventoryId,
                                   Integer sizeId, String reason) {
        return exchangeRepository.create(stationId, memberId, itemId, inventoryId, sizeId, reason);
    }

    public Optional<ExchangeRequest> findById(int id) {
        return exchangeRepository.findById(id);
    }

    public List<ExchangeRequest> findByStation(int stationId) {
        return exchangeRepository.findByStation(stationId);
    }

    public List<ExchangeRequest> findByMember(int memberId) {
        return exchangeRepository.findByMember(memberId);
    }

    public ExchangeRequest updateStatus(int id, ExchangeStatus newStatus, int changedBy, String note) {
        var request = exchangeRepository.findById(id)
                .orElseThrow(() -> new BadRequestResponse("Exchange request not found"));
        var oldStatus = request.status();
        exchangeRepository.updateStatus(id, newStatus);
        exchangeRepository.createLog(id, oldStatus, newStatus, changedBy, note);
        return exchangeRepository.findById(id).orElseThrow();
    }

    public List<ExchangeLog> findLogs(int requestId) {
        return exchangeRepository.findLogs(requestId);
    }

    public boolean delete(int id) {
        return exchangeRepository.delete(id);
    }
}
