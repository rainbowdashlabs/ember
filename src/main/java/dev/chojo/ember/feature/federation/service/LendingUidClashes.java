/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lending requests that arrive with a moved station on the instance where the partner they are
 * shared with already runs.
 *
 * <p>A request between stations of two instances is kept on both of them under one uid. When one of
 * the two moves to the instance of the other, its copy arrives where the other copy already is, and
 * between two stations of one instance a request is one row both read. So the arriving copy is
 * written under a stand-in uid first, which lets the run carry its lines, the pieces set aside on
 * them, the borrowed copies that came on them and its messages over the way it carries everything
 * else. Once the run has settled, the arrived copy is merged into the partner's: lines are paired by
 * position, which both copies share, what one leaves empty is taken from the other, and the arrived
 * copy goes.
 */
@Singleton
public class LendingUidClashes {
    private static final Logger log = LoggerFactory.getLogger(LendingUidClashes.class);

    private final LendingRepository repository;

    @Inject
    public LendingUidClashes(LendingRepository repository) {
        this.repository = repository;
    }

    /**
     * Gives every arriving request whose uid a request here already carries a stand-in uid.
     *
     * @param rows      the arriving request rows
     * @param standIns  where each stand-in is recorded with the uid it stands in for
     * @return the rows to write, the clashing ones under their stand-in
     */
    public List<Map<String, Object>> setAside(List<Map<String, Object>> rows, Map<UUID, UUID> standIns) {
        var written = new ArrayList<Map<String, Object>>(rows.size());
        for (Map<String, Object> row : rows) {
            UUID uid = uidOf(row);
            if (uid == null || repository.findRequestByUid(uid).isEmpty()) {
                written.add(row);
                continue;
            }
            UUID standIn = UUID.randomUUID();
            var renamed = new LinkedHashMap<>(row);
            renamed.put("uid", standIn.toString());
            written.add(renamed);
            standIns.put(standIn, uid);
        }
        return written;
    }

    /**
     * Merges every request that arrived under a stand-in into the copy its partner keeps here.
     *
     * <p>The arrived lines are put in the order they had at the source, by their source ids: the run
     * writes a line only once what it names has arrived, so their ids here need not keep that order.
     *
     * @param standIns      each stand-in with the uid it stands in for
     * @param sourceLineIds the source id of each arrived line, keyed by the line's id here
     * @return how many requests were merged
     */
    public int merge(Map<UUID, UUID> standIns, Map<Integer, Integer> sourceLineIds) {
        int merged = 0;
        for (var standIn : standIns.entrySet()) {
            var arrived = repository.findRequestByUid(standIn.getKey()).orElse(null);
            var kept = repository.findRequestByUid(standIn.getValue()).orElse(null);
            if (arrived == null || kept == null) continue;
            if (!sameParties(arrived, kept)) {
                log.warn(
                        "Lending request {} arrived where another request between other stations carries its uid; it is kept apart as {}",
                        standIn.getValue(),
                        standIn.getKey());
                continue;
            }
            Transactions.run(() -> join(arrived, kept, sourceLineIds));
            merged++;
        }
        return merged;
    }

    private void join(LendingRequest arrived, LendingRequest kept, Map<Integer, Integer> sourceLineIds) {
        var arrivedLines = repository.findItemsByRequest(arrived.id()).stream()
                .sorted(Comparator.comparingInt(
                                (LendingRequestItem line) -> sourceLineIds.getOrDefault(line.id(), Integer.MAX_VALUE))
                        .thenComparingInt(LendingRequestItem::id))
                .toList();
        var keptLines = repository.findItemsByRequest(kept.id());
        int joined = Math.min(arrivedLines.size(), keptLines.size());
        for (int position = 0; position < arrivedLines.size(); position++) {
            int line = arrivedLines.get(position).id();
            if (position < joined) {
                repository.joinLine(line, keptLines.get(position).id());
            } else {
                repository.moveLine(line, kept.id());
            }
        }
        repository.joinRequest(arrived.id(), kept.id());
        repository.nameStationsHere(kept.id());
        log.info(
                "Lending request {} arrived with a station whose partner keeps it here and was merged into that copy: {} line(s) joined, {} line(s) added",
                kept.uid(),
                joined,
                arrivedLines.size() - joined);
    }

    private static boolean sameParties(LendingRequest arrived, LendingRequest kept) {
        return arrived.requestingStationUid().equals(kept.requestingStationUid())
                && arrived.owningStationUid().equals(kept.owningStationUid());
    }

    private static @Nullable UUID uidOf(Map<String, Object> row) {
        Object raw = row.get("uid");
        if (raw instanceof UUID uid) return uid;
        if (!(raw instanceof String text)) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
