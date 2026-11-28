/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.inventory.entity.InventoryMatch;
import dev.chojo.ember.util.sql.FullTextSearch;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Searches a station's inventories, kinds and pieces by name and description.
 *
 * <p>The text vectors are built per query rather than stored. A stored vector needs one fixed text
 * search configuration, and each station searches in its own language. A station holds at most a few
 * thousand pieces, so reading them without an index is fast enough.
 *
 * <p>Every word of the search has to be found in the same row, either as part of the name or as a
 * word of name or description in the station's language. Part of the name finds the part of a compound
 * word ({@code funk} in {@code Handfunkgerät}) that no stemming does; the language finds a word in
 * another form ({@code Einsatz} in {@code Einsätze}).
 */
@Singleton
public class InventorySearchRepository {

    /**
     * The inventories of a station whose own name or description matches every word.
     *
     * @param stationId the station
     * @param words     the words searched for, at least one
     * @param config    the text search configuration of the station
     * @return one hit per matching inventory, its id as {@link InventoryMatch.Hit#id()}
     */
    public List<InventoryMatch.Hit> inventories(int stationId, List<String> words, String config) {
        return search("""
                        SELECT i.id, i.name, NULL::INT AS inventory_id, i.description
                        FROM inventory i
                        WHERE i.station_id = :station_id""", stationId, words, config).stream().map(Owned::hit).toList();
    }

    /**
     * The kinds of a station's inventories whose name or description matches every word.
     *
     * @param stationId the station
     * @param words     the words searched for, at least one
     * @param config    the text search configuration of the station
     * @return one entry per matching kind, paired with its inventory
     */
    public List<Owned> kinds(int stationId, List<String> words, String config) {
        return search("""
                SELECT a.id, a.name, a.inventory_id, a.description
                FROM inventory_art a
                JOIN inventory i ON i.id = a.inventory_id
                WHERE i.station_id = :station_id""", stationId, words, config);
    }

    /**
     * The pieces of a station's inventories whose name or description matches every word.
     *
     * @param stationId the station
     * @param words     the words searched for, at least one
     * @param config    the text search configuration of the station
     * @return one entry per matching piece, paired with its inventory
     */
    public List<Owned> pieces(int stationId, List<String> words, String config) {
        return search("""
                SELECT p.id, p.name, p.inventory_id, p.description
                FROM inventory_item p
                JOIN inventory i ON i.id = p.inventory_id
                WHERE i.station_id = :station_id""", stationId, words, config);
    }

    /**
     * Runs one search over the rows {@code source} selects, which carry {@code id}, {@code name},
     * {@code inventory_id} and {@code description}. The vector of each row is built once and used for
     * both the condition and the rank. A name holding a word counts as a full hit for that word, the
     * text search adds its own rank on top. A word the language drops as too common ("und", "mit")
     * leaves an empty text query; it counts as found, as long as some other word is found for real,
     * so a search of nothing but such words does not match every row.
     */
    private List<Owned> search(String source, int stationId, List<String> words, String config) {
        String cfg = FullTextSearch.config(config);
        var conditions = new ArrayList<String>();
        var anyHit = new ArrayList<String>();
        var nameHits = new ArrayList<String>();
        Call call = call().bind("station_id", stationId);
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            String term = FullTextSearch.prefixTerms(word);
            String like = "like_" + letter(index);
            call = call.bind(like, SqlSupport.containing(word));
            nameHits.add("CASE WHEN r.name ILIKE :%s THEN 1.0 ELSE 0.0 END".formatted(like));
            if (term.isEmpty()) {
                conditions.add("r.name ILIKE :%s".formatted(like));
                anyHit.add("r.name ILIKE :%s".formatted(like));
            } else {
                String termBind = "term_" + letter(index);
                call = call.bind(termBind, term);
                String tsQuery = FullTextSearch.prefixQuery(cfg, termBind);
                conditions.add("(r.name ILIKE :%1$s OR numnode(%2$s) = 0 OR r.vec @@ %2$s)".formatted(like, tsQuery));
                anyHit.add("r.name ILIKE :%1$s OR r.vec @@ %2$s".formatted(like, tsQuery));
            }
        }
        String all = words.stream()
                .map(FullTextSearch::prefixTerms)
                .filter(term -> !term.isEmpty())
                .collect(Collectors.joining(" & "));
        String textRank = "0.0";
        if (!all.isEmpty()) {
            call = call.bind("all", all);
            textRank = "ts_rank(r.vec, %s)".formatted(FullTextSearch.prefixQuery(cfg, "all"));
        }
        String rank = "(%s) / %d + %s".formatted(String.join(" + ", nameHits), words.size(), textRank);
        return query(
                        """
                        SELECT r.id, r.name, r.inventory_id, %s AS rank
                        FROM (
                            SELECT s.*,
                                   setweight(to_tsvector('%s', coalesce(s.name, '')), 'A')
                                       || setweight(to_tsvector('%s', coalesce(s.description, '')), 'B') AS vec
                            FROM (%s) s
                        ) r
                        WHERE %s;""",
                        rank,
                        cfg,
                        cfg,
                        source,
                        String.join(" AND ", conditions) + " AND (" + String.join(" OR ", anyHit) + ")")
                .single(call)
                .map(row -> new Owned(
                        row.getInt("inventory_id"),
                        new InventoryMatch.Hit(row.getInt("id"), row.getString("name"), row.getDouble("rank"))))
                .all();
    }

    /** Bind names may not hold digits, so the n-th word is distinguished by the n-th letter. */
    private static char letter(int index) {
        return (char) ('a' + index);
    }

    /**
     * A kind or a piece that matched, with the inventory it belongs to.
     *
     * @param inventoryId the inventory; {@code 0} for an inventory matching itself
     * @param hit         the kind, piece or inventory
     */
    public record Owned(int inventoryId, InventoryMatch.Hit hit) {}
}
