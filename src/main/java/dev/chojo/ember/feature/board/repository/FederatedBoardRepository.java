/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedAssignee;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedCommentAuthor;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedCreator;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedWatcher;
import dev.chojo.ember.feature.board.entity.FederationBoardBookmark;
import dev.chojo.ember.feature.board.entity.FederationBoardShare;
import dev.chojo.ember.feature.board.entity.FederationBoardShareTarget;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class FederatedBoardRepository {

    // -- Board Share --

    public FederationBoardShare createShare(int boardId) {
        return Query.query("INSERT INTO federation_board_share(board_id) VALUES (:board_id) RETURNING *;")
                .single(Call.of().bind("board_id", boardId))
                .map(FederationBoardShare.map())
                .first()
                .orElseThrow();
    }

    public Optional<FederationBoardShare> findShare(int boardId) {
        return Query.query("SELECT * FROM federation_board_share WHERE board_id = :board_id;")
                .single(Call.of().bind("board_id", boardId))
                .map(FederationBoardShare.map())
                .first();
    }

    public void deleteShare(int boardId) {
        Query.query("DELETE FROM federation_board_share WHERE board_id = :board_id;")
                .single(Call.of().bind("board_id", boardId))
                .delete();
    }

    // -- Share Targets --

    public void setShareTarget(int shareId, int partnerId, BoardShareMode shareMode) {
        Query.query("""
                        INSERT INTO federation_board_share_target(share_id, partner_id, share_mode)
                        VALUES (:share_id, :partner_id, :share_mode)
                        ON CONFLICT (share_id, partner_id) DO UPDATE SET share_mode = :share_mode;""")
                .single(Call.of()
                        .bind("share_id", shareId)
                        .bind("partner_id", partnerId)
                        .bind("share_mode", shareMode.name()))
                .insert();
    }

    public void removeShareTarget(int shareId, int partnerId) {
        Query.query(
                        "DELETE FROM federation_board_share_target WHERE share_id = :share_id AND partner_id = :partner_id;")
                .single(Call.of().bind("share_id", shareId).bind("partner_id", partnerId))
                .delete();
    }

    public void clearShareTargets(int shareId) {
        Query.query("DELETE FROM federation_board_share_target WHERE share_id = :share_id;")
                .single(Call.of().bind("share_id", shareId))
                .delete();
    }

    public List<FederationBoardShareTarget> findShareTargets(int shareId) {
        return Query.query("SELECT * FROM federation_board_share_target WHERE share_id = :share_id;")
                .single(Call.of().bind("share_id", shareId))
                .map(FederationBoardShareTarget.map())
                .all();
    }

    public Optional<BoardShareMode> findShareMode(int boardId, int partnerId) {
        return Query.query("""
                        SELECT t.share_mode FROM federation_board_share_target t
                        JOIN federation_board_share s ON s.id = t.share_id
                        WHERE s.board_id = :board_id AND t.partner_id = :partner_id;""")
                .single(Call.of().bind("board_id", boardId).bind("partner_id", partnerId))
                .map(row -> BoardShareMode.valueOf(row.getString("share_mode")))
                .first();
    }

    public List<Integer> findSharedBoardIds(int partnerId) {
        return Query.query("""
                        SELECT s.board_id FROM federation_board_share s
                        JOIN federation_board_share_target t ON t.share_id = s.id
                        WHERE t.partner_id = :partner_id;""")
                .single(Call.of().bind("partner_id", partnerId))
                .map(row -> row.getInt("board_id"))
                .all();
    }

    // -- Federated Edit Roles --

    public void setFederatedEditRoles(int boardId, List<Integer> roleIds) {
        Query.query("DELETE FROM federation_board_edit_role WHERE board_id = :board_id;")
                .single(Call.of().bind("board_id", boardId))
                .delete();
        for (int roleId : roleIds) {
            Query.query("INSERT INTO federation_board_edit_role(board_id, role_id) VALUES (:board_id, :role_id);")
                    .single(Call.of().bind("board_id", boardId).bind("role_id", roleId))
                    .insert();
        }
    }

    public List<Integer> findFederatedEditRoles(int boardId) {
        return Query.query("SELECT role_id FROM federation_board_edit_role WHERE board_id = :board_id;")
                .single(Call.of().bind("board_id", boardId))
                .map(row -> row.getInt("role_id"))
                .all();
    }

    public boolean hasFederatedEditRoles(int boardId) {
        return Query.query("SELECT 1 FROM federation_board_edit_role WHERE board_id = :board_id LIMIT 1;")
                .single(Call.of().bind("board_id", boardId))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    // -- Federated Assignees --

    public void setFederatedAssignee(int ticketId, int partnerId, String remoteMemberId) {
        Query.query("""
                        INSERT INTO board_ticket_federated_assignee(ticket_id, partner_id, remote_member_id)
                        VALUES (:ticket_id, :partner_id, :remote_member_id)
                        ON CONFLICT (ticket_id) DO UPDATE SET partner_id = :partner_id, remote_member_id = :remote_member_id;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId))
                .insert();
    }

    public void removeFederatedAssignee(int ticketId) {
        Query.query("DELETE FROM board_ticket_federated_assignee WHERE ticket_id = :ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId))
                .delete();
    }

    public Optional<BoardTicketFederatedAssignee> findFederatedAssignee(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_federated_assignee WHERE ticket_id = :ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketFederatedAssignee.map())
                .first();
    }

    // -- Federated Comment Authors --

    public void setFederatedCommentAuthor(int commentId, int partnerId, String remoteMemberId) {
        Query.query("""
                        INSERT INTO board_ticket_federated_comment_author(comment_id, partner_id, remote_member_id)
                        VALUES (:comment_id, :partner_id, :remote_member_id);""")
                .single(Call.of()
                        .bind("comment_id", commentId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId))
                .insert();
    }

    public Optional<BoardTicketFederatedCommentAuthor> findFederatedCommentAuthor(int commentId) {
        return Query.query("SELECT * FROM board_ticket_federated_comment_author WHERE comment_id = :comment_id;")
                .single(Call.of().bind("comment_id", commentId))
                .map(BoardTicketFederatedCommentAuthor.map())
                .first();
    }

    // -- Federated Creators --

    public void setFederatedCreator(int ticketId, int partnerId, String remoteMemberId) {
        Query.query("""
                        INSERT INTO board_ticket_federated_creator(ticket_id, partner_id, remote_member_id)
                        VALUES (:ticket_id, :partner_id, :remote_member_id);""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId))
                .insert();
    }

    public Optional<BoardTicketFederatedCreator> findFederatedCreator(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_federated_creator WHERE ticket_id = :ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketFederatedCreator.map())
                .first();
    }

    // -- Federated Watchers --

    public void addFederatedWatcher(int ticketId, int partnerId, String remoteMemberId) {
        Query.query("""
                        INSERT INTO board_ticket_federated_watcher(ticket_id, partner_id, remote_member_id)
                        VALUES (:ticket_id, :partner_id, :remote_member_id) ON CONFLICT DO NOTHING;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId))
                .insert();
    }

    public void removeFederatedWatcher(int ticketId, int partnerId, String remoteMemberId) {
        Query.query("""
                        DELETE FROM board_ticket_federated_watcher
                        WHERE ticket_id = :ticket_id AND partner_id = :partner_id AND remote_member_id = :remote_member_id;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId))
                .delete();
    }

    public List<BoardTicketFederatedWatcher> findFederatedWatchers(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_federated_watcher WHERE ticket_id = :ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketFederatedWatcher.map())
                .all();
    }

    public boolean isFederatedWatching(int ticketId, int partnerId, String remoteMemberId) {
        return Query.query("""
                        SELECT 1 FROM board_ticket_federated_watcher
                        WHERE ticket_id = :ticket_id AND partner_id = :partner_id AND remote_member_id = :remote_member_id;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    // -- Bookmarks --

    public FederationBoardBookmark createBookmark(
            int memberId,
            int partnerId,
            int remoteBoardId,
            String remoteBoardName,
            String remoteBoardShortKey,
            BoardShareMode shareMode) {
        return Query.query("""
                        INSERT INTO federation_board_bookmark(member_id, partner_id, remote_board_id, remote_board_name, remote_board_short_key, share_mode)
                        VALUES (:member_id, :partner_id, :remote_board_id, :remote_board_name, :remote_board_short_key, :share_mode) RETURNING *;""")
                .single(Call.of()
                        .bind("member_id", memberId)
                        .bind("partner_id", partnerId)
                        .bind("remote_board_id", remoteBoardId)
                        .bind("remote_board_name", remoteBoardName)
                        .bind("remote_board_short_key", remoteBoardShortKey)
                        .bind("share_mode", shareMode.name()))
                .map(FederationBoardBookmark.map())
                .first()
                .orElseThrow();
    }

    public void deleteBookmark(int bookmarkId) {
        Query.query("DELETE FROM federation_board_bookmark WHERE id = :id;")
                .single(Call.of().bind("id", bookmarkId))
                .delete();
    }

    public void deleteBookmarkByBoard(int memberId, int partnerId, int remoteBoardId) {
        Query.query("""
                        DELETE FROM federation_board_bookmark
                        WHERE member_id = :member_id AND partner_id = :partner_id AND remote_board_id = :remote_board_id;""")
                .single(Call.of()
                        .bind("member_id", memberId)
                        .bind("partner_id", partnerId)
                        .bind("remote_board_id", remoteBoardId))
                .delete();
    }

    public List<FederationBoardBookmark> findBookmarks(int memberId) {
        return Query.query("SELECT * FROM federation_board_bookmark WHERE member_id = :member_id ORDER BY created_at;")
                .single(Call.of().bind("member_id", memberId))
                .map(FederationBoardBookmark.map())
                .all();
    }

    public void updateBookmarkName(int partnerId, int remoteBoardId, String newName, String newShortKey) {
        Query.query("""
                        UPDATE federation_board_bookmark SET remote_board_name = :name, remote_board_short_key = :short_key
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id;""")
                .single(Call.of()
                        .bind("partner_id", partnerId)
                        .bind("remote_board_id", remoteBoardId)
                        .bind("name", newName)
                        .bind("short_key", newShortKey))
                .update();
    }

    public void deleteBookmarksByBoard(int partnerId, int remoteBoardId) {
        Query.query(
                        "DELETE FROM federation_board_bookmark WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id;")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .delete();
    }

    public void updateBookmarkShareMode(int partnerId, int remoteBoardId, BoardShareMode shareMode) {
        Query.query("""
                        UPDATE federation_board_bookmark SET share_mode = :share_mode
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id;""")
                .single(Call.of()
                        .bind("partner_id", partnerId)
                        .bind("remote_board_id", remoteBoardId)
                        .bind("share_mode", shareMode.name()))
                .update();
    }

    // -- Local Overrides --

    public void setLocalViewOverride(int partnerId, int remoteBoardId, AccessData access) {
        Query.query("""
                        DELETE FROM federation_board_local_view_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .delete();
        for (int roleId : access.roleIds()) {
            Query.query("""
                            INSERT INTO federation_board_local_view_override(partner_id, remote_board_id, role_id)
                            VALUES (:partner_id, :remote_board_id, :role_id);""")
                    .single(Call.of()
                            .bind("partner_id", partnerId)
                            .bind("remote_board_id", remoteBoardId)
                            .bind("role_id", roleId))
                    .insert();
        }
        for (int groupId : access.groupIds()) {
            Query.query("""
                            INSERT INTO federation_board_local_view_override(partner_id, remote_board_id, group_id)
                            VALUES (:partner_id, :remote_board_id, :group_id);""")
                    .single(Call.of()
                            .bind("partner_id", partnerId)
                            .bind("remote_board_id", remoteBoardId)
                            .bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : access.tagIds()) {
            Query.query("""
                            INSERT INTO federation_board_local_view_override(partner_id, remote_board_id, tag_id)
                            VALUES (:partner_id, :remote_board_id, :tag_id);""")
                    .single(Call.of()
                            .bind("partner_id", partnerId)
                            .bind("remote_board_id", remoteBoardId)
                            .bind("tag_id", tagId))
                    .insert();
        }
    }

    public void setLocalEditOverride(int partnerId, int remoteBoardId, AccessData access) {
        Query.query("""
                        DELETE FROM federation_board_local_edit_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .delete();
        for (int roleId : access.roleIds()) {
            Query.query("""
                            INSERT INTO federation_board_local_edit_override(partner_id, remote_board_id, role_id)
                            VALUES (:partner_id, :remote_board_id, :role_id);""")
                    .single(Call.of()
                            .bind("partner_id", partnerId)
                            .bind("remote_board_id", remoteBoardId)
                            .bind("role_id", roleId))
                    .insert();
        }
        for (int groupId : access.groupIds()) {
            Query.query("""
                            INSERT INTO federation_board_local_edit_override(partner_id, remote_board_id, group_id)
                            VALUES (:partner_id, :remote_board_id, :group_id);""")
                    .single(Call.of()
                            .bind("partner_id", partnerId)
                            .bind("remote_board_id", remoteBoardId)
                            .bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : access.tagIds()) {
            Query.query("""
                            INSERT INTO federation_board_local_edit_override(partner_id, remote_board_id, tag_id)
                            VALUES (:partner_id, :remote_board_id, :tag_id);""")
                    .single(Call.of()
                            .bind("partner_id", partnerId)
                            .bind("remote_board_id", remoteBoardId)
                            .bind("tag_id", tagId))
                    .insert();
        }
    }

    public AccessData findLocalViewOverride(int partnerId, int remoteBoardId) {
        var roleIds = Query.query("""
                        SELECT role_id FROM federation_board_local_view_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id AND role_id IS NOT NULL;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> row.getInt("role_id"))
                .all();
        var groupIds = Query.query("""
                        SELECT group_id FROM federation_board_local_view_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id AND group_id IS NOT NULL;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> row.getInt("group_id"))
                .all();
        var tagIds = Query.query("""
                        SELECT tag_id FROM federation_board_local_view_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id AND tag_id IS NOT NULL;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> row.getInt("tag_id"))
                .all();
        return new AccessData(roleIds, groupIds, tagIds);
    }

    public AccessData findLocalEditOverride(int partnerId, int remoteBoardId) {
        var roleIds = Query.query("""
                        SELECT role_id FROM federation_board_local_edit_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id AND role_id IS NOT NULL;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> row.getInt("role_id"))
                .all();
        var groupIds = Query.query("""
                        SELECT group_id FROM federation_board_local_edit_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id AND group_id IS NOT NULL;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> row.getInt("group_id"))
                .all();
        var tagIds = Query.query("""
                        SELECT tag_id FROM federation_board_local_edit_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id AND tag_id IS NOT NULL;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> row.getInt("tag_id"))
                .all();
        return new AccessData(roleIds, groupIds, tagIds);
    }

    public boolean hasLocalViewOverride(int partnerId, int remoteBoardId) {
        return Query.query("""
                        SELECT 1 FROM federation_board_local_view_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id LIMIT 1;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    public boolean hasLocalEditOverride(int partnerId, int remoteBoardId) {
        return Query.query("""
                        SELECT 1 FROM federation_board_local_edit_override
                        WHERE partner_id = :partner_id AND remote_board_id = :remote_board_id LIMIT 1;""")
                .single(Call.of().bind("partner_id", partnerId).bind("remote_board_id", remoteBoardId))
                .map(row -> true)
                .first()
                .orElse(false);
    }
}
