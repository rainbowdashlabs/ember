/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.event.events.NewsDeleted;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.entity.NewsViewer;
import dev.chojo.ember.feature.news.repository.NewsRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service layer for managing news articles and comments.
 * Handles creation with group restrictions, updates, deletions, and comment operations.
 */
@Singleton
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");
    private static final Pattern MENTION_PATTERN_LEGACY = Pattern.compile("@\\[(\\d+):([^\\]]+)]");
    private static final Pattern BULK_MENTION_PATTERN =
            Pattern.compile("@\\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\\d+)]");

    private final NewsRepository newsRepository;
    private final RestrictionService restrictionService;
    private final DomainEventBus eventBus;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public NewsService(
            NewsRepository newsRepository,
            RestrictionService restrictionService,
            DomainEventBus eventBus,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.newsRepository = newsRepository;
        this.restrictionService = restrictionService;
        this.eventBus = eventBus;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Derives a plain-text preview from a Markdown article body. Strips the most common
     * formatting (headings, emphasis, lists, code fences, links → label) but preserves
     * paragraph structure (single newlines kept, runs of 3+ newlines collapsed to a single
     * paragraph break) so the feed renderer can re-flow it as multi-line HTML. Markdown
     * tables are converted to {@code "col · col · col"} lines so they read as structured
     * key/value pairs instead of dumping raw {@code |} characters. The renderer applies its
     * own length cap on top — we just hand it readable plain text. Returns {@code null}
     * when the input is blank.
     */
    static String previewOf(String markdown) {
        if (markdown == null || markdown.isBlank()) return null;
        String stripped = markdown
                // Fenced code blocks add nothing useful in plain text — drop them entirely.
                .replaceAll("(?s)```.*?```", "")
                // `[label](url)` → keep the label.
                .replaceAll("\\[([^\\]]+)]\\([^)]+\\)", "$1")
                // Markdown table separator rows (|---|---|---|, with optional spaces / colons
                // for alignment) carry no content; strip the whole line.
                .replaceAll("(?m)^\\s*\\|?[\\s:|-]+\\|?\\s*$\\n?", "")
                // Trim leading / trailing pipes from each table data row.
                .replaceAll("(?m)^\\s*\\|", "")
                .replaceAll("(?m)\\|\\s*$", "")
                // Cell separator: convert " | " into a middle-dot so columns stay visually
                // grouped without dumping bare pipes into the body.
                .replace(" | ", " · ")
                // Heading markers and blockquote arrows at line start.
                .replaceAll("(?m)^\\s*#{1,6}\\s+", "")
                .replaceAll("(?m)^\\s*>\\s+", "")
                // Inline emphasis / inline-code markers.
                .replaceAll("[*_`]+", "")
                // Bullet / numbered list markers at line start — keep a bullet glyph so the
                // structure survives the strip.
                .replaceAll("(?m)^\\s*[-+]\\s+", "• ")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
                // Collapse runs of 3+ newlines into a single paragraph break, but keep
                // single newlines so the source's line structure flows into the body.
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return stripped.isBlank() ? null : stripped;
    }

    /**
     * Creates a news article and optionally applies group restrictions.
     *
     * @param stationId       the station to publish in
     * @param title           article title
     * @param contentMarkdown article body in Markdown
     * @param contentHtml     article body as HTML
     * @param author          identity of the author
     * @param groupIds        group IDs to restrict visibility to (empty for unrestricted)
     * @return the newly created news entry
     */
    public News create(
            int stationId,
            String title,
            String contentMarkdown,
            String contentHtml,
            MemberIdentity author,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        var news = newsRepository.create(stationId, title, contentMarkdown, contentHtml, author);
        setRestrictions(news.id(), new RestrictionSelection(userTypes, groupIds, tagIds, memberIds, null));
        String authorName = resolveAuthorName(stationId, author);
        eventBus.publish(new NewsCreated(stationId, news.id(), title, authorName, previewOf(contentMarkdown)));
        log.info("Created news {} on station {}", news.id(), stationId);
        return news;
    }

    /**
     * Finds a news article by its ID.
     *
     * @param id the news article ID
     * @return the news article, or empty if not found
     */
    public Optional<News> findById(int id) {
        return newsRepository.findById(id);
    }

    /**
     * Retrieves news articles for a station with pagination.
     *
     * @param stationId the station ID
     * @param offset    pagination offset
     * @param limit     maximum number of results
     * @return list of news articles
     */
    public List<News> findByStation(int stationId, int offset, int limit) {
        return newsRepository.findByStation(stationId, offset, limit);
    }

    /**
     * Retrieves published news visible to a specific member, respecting group restrictions.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @param offset    pagination offset
     * @param limit     maximum number of results
     * @return list of visible news articles
     */
    public List<News> findVisibleForMember(int stationId, int memberId, int offset, int limit) {
        return newsRepository.findVisibleForMember(stationId, memberId, offset, limit);
    }

    /**
     * Updates a news article's content and group restrictions.
     *
     * @param id              the news article ID
     * @param title           new title
     * @param contentMarkdown new Markdown content
     * @param contentHtml     new HTML content
     * @param groupIds        new group restriction IDs
     * @return the updated news article, or empty if the article was not found
     */
    public Optional<News> update(
            int id,
            String title,
            String contentMarkdown,
            String contentHtml,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        if (newsRepository.update(id, title, contentMarkdown, contentHtml)) {
            setRestrictions(id, new RestrictionSelection(userTypes, groupIds, tagIds, memberIds, null));
            log.info("Updated news {}", id);
            return newsRepository.findById(id);
        }
        log.warn("Update for news {} affected zero rows", id);
        return Optional.empty();
    }

    /**
     * Deletes a news article by its ID.
     *
     * @param id the news article ID
     */
    public void updatePublicBlog(int id, boolean publicBlog) {
        newsRepository.updatePublicBlog(id, publicBlog);
        log.info("Updated news {} publicBlog={}", id, publicBlog);
    }

    public List<News> findPublicBlogEntries(int stationId, int offset, int limit) {
        return newsRepository.findPublicBlogEntries(stationId, offset, limit);
    }

    /**
     * Lists published, unrestricted public news with optional case-insensitive search on title or
     * body. Backs the public news search endpoint used by the {@code NEWS_TEASER} cell picker.
     */
    public List<News> findPublicBlogEntries(int stationId, String search, int offset, int limit) {
        return newsRepository.findPublicBlogEntries(stationId, search, offset, limit);
    }

    public boolean hasPublicBlogEntries(int stationId) {
        return newsRepository.hasPublicBlogEntries(stationId);
    }

    public boolean delete(int id) {
        var news = newsRepository.findById(id).orElse(null);
        if (news == null) {
            log.warn("Delete for news {} skipped: not found", id);
            return false;
        }
        if (newsRepository.delete(id)) {
            eventBus.publish(new NewsDeleted(news.stationId(), id, news.title()));
            log.info("Deleted news {} on station {}", id, news.stationId());
            return true;
        }
        log.warn("Delete for news {} affected zero rows", id);
        return false;
    }

    /**
     * Records that a member fully saw a news entry in their viewport. Idempotent —
     * repeated views by the same member are silently ignored.
     */
    public void recordView(int newsId, int memberId) {
        newsRepository.recordView(newsId, memberId);
    }

    /**
     * Counts how many distinct members have viewed a news entry.
     */
    public int countViews(int newsId) {
        return newsRepository.countViews(newsId);
    }

    /**
     * Checks whether a specific member has viewed a news entry.
     */
    public boolean hasViewed(int newsId, int memberId) {
        return newsRepository.hasViewed(newsId, memberId);
    }

    /**
     * Returns the two lists shown in the views modal: who has seen the news (with the
     * timestamp of their first view, newest first) and who is eligible to see it but
     * has not yet been observed viewing it.
     */
    public ViewerSummary findViewerSummary(int newsId, int stationId) {
        return new ViewerSummary(
                newsRepository.findSeenViewers(newsId), newsRepository.findUnseenViewers(newsId, stationId));
    }

    /**
     * Retrieves the restriction set for a news article.
     */
    public RestrictionSet findRestrictions(int newsId) {
        var news = newsRepository.findById(newsId).orElse(null);
        RestrictionMode mode = news != null ? news.restrictionMode() : RestrictionMode.AND;
        return restrictionService.findRestrictionSet(RestrictionType.NEWS, newsId, mode);
    }

    /**
     * Sets all restrictions for a news article.
     */
    public void setRestrictions(int newsId, RestrictionSelection selection) {
        restrictionService.setRestrictions(RestrictionType.NEWS, newsId, selection);
    }

    /**
     * Counts the total number of comments on a news article.
     *
     * @param newsId the news article ID
     * @return comment count
     */
    public int countComments(int newsId) {
        return newsRepository.countComments(newsId);
    }

    /**
     * Creates a comment on a news article.
     *
     * @param newsId     the news article ID
     * @param parentId   parent comment ID for replies, or {@code null} for top-level comments
     * @param author     identity of the comment author, or {@code null} for federated/system comments
     * @param authorName display name of the comment author
     * @param content    comment text
     * @return the newly created comment
     */
    public NewsComment createComment(
            int stationId, int newsId, Integer parentId, MemberIdentity author, String authorName, String content) {
        var comment = newsRepository.createComment(newsId, parentId, author, content);
        log.info("Created news comment {} on news {} (station {})", comment.id(), newsId, stationId);
        var news = newsRepository.findById(newsId).orElse(null);
        if (news != null) {
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            Integer parentAuthorMemberId = null;
            if (parentId != null) {
                var parentComment = newsRepository.findCommentById(parentId).orElse(null);
                if (parentComment != null && parentComment.author() != null) {
                    parentAuthorMemberId = stationMemberRepository
                            .resolveId(stationId, parentComment.author().memberUid())
                            .orElse(null);
                }
            }
            Integer authorMemberId = author != null
                    ? stationMemberRepository
                            .resolveId(stationId, author.memberUid())
                            .orElse(null)
                    : null;
            eventBus.publish(new CommentCreated(
                    stationId,
                    CommentEntityType.NEWS,
                    newsId,
                    news.title(),
                    comment.id(),
                    parentId,
                    parentAuthorMemberId,
                    authorMemberId,
                    authorName,
                    preview));

            if (authorMemberId != null) {
                var matcher = MENTION_PATTERN.matcher(content);
                while (matcher.find()) {
                    try {
                        var memberUid = UUID.fromString(matcher.group(2));
                        stationMemberRepository.resolveId(stationId, memberUid).ifPresent(mentionedId -> {
                            if (!mentionedId.equals(authorMemberId)) {
                                eventBus.publish(new MentionedInComment(
                                        stationId,
                                        mentionedId,
                                        authorMemberId,
                                        authorName,
                                        CommentEntityType.NEWS,
                                        newsId,
                                        news.title(),
                                        preview));
                            }
                        });
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                var legacyMatcher = MENTION_PATTERN_LEGACY.matcher(content);
                while (legacyMatcher.find()) {
                    int mentionedId = Integer.parseInt(legacyMatcher.group(1));
                    if (mentionedId != authorMemberId) {
                        eventBus.publish(new MentionedInComment(
                                stationId,
                                mentionedId,
                                authorMemberId,
                                authorName,
                                CommentEntityType.NEWS,
                                newsId,
                                news.title(),
                                preview));
                    }
                }
                var bulkMatcher = BULK_MENTION_PATTERN.matcher(content);
                while (bulkMatcher.find()) {
                    var type = MentionType.valueOf(bulkMatcher.group(1));
                    int targetId = Integer.parseInt(bulkMatcher.group(3));
                    eventBus.publish(new BulkMentionedInComment(
                            stationId,
                            authorMemberId,
                            authorName,
                            CommentEntityType.NEWS,
                            newsId,
                            news.title(),
                            type,
                            targetId,
                            preview));
                }
            }
        }
        return comment;
    }

    // -- Comments --

    /**
     * Retrieves all comments for a news article.
     *
     * @param newsId the news article ID
     * @return list of comments
     */
    public List<NewsComment> findComments(int newsId) {
        return newsRepository.findCommentsByNews(newsId);
    }

    /**
     * Finds a comment by its ID.
     *
     * @param id the comment ID
     * @return the comment, or empty if not found
     */
    public Optional<NewsComment> findCommentById(int id) {
        return newsRepository.findCommentById(id);
    }

    /**
     * Updates the content of a comment.
     *
     * @param id      the comment ID
     * @param content new comment text
     * @return {@code true} if the comment was updated
     */
    public boolean updateComment(int id, String content) {
        boolean updated = newsRepository.updateComment(id, content);
        if (updated) {
            log.info("Updated news comment {}", id);
        } else {
            log.warn("Update for news comment {} affected zero rows", id);
        }
        return updated;
    }

    /**
     * Deletes a comment by its ID.
     *
     * @param id the comment ID
     * @return {@code true} if the comment was deleted
     */
    public boolean deleteComment(int stationId, int id) {
        var comment = newsRepository.findCommentById(id).orElse(null);
        if (comment == null) {
            log.warn("Delete for news comment {} skipped: not found", id);
            return false;
        }
        if (newsRepository.deleteComment(id)) {
            String preview =
                    comment.content().length() > 100 ? comment.content().substring(0, 100) + "..." : comment.content();
            eventBus.publish(new CommentDeleted(stationId, id, preview));
            log.info("Deleted news comment {} on station {}", id, stationId);
            return true;
        }
        log.warn("Delete for news comment {} affected zero rows", id);
        return false;
    }

    private String resolveAuthorName(int stationId, MemberIdentity author) {
        if (author == null) return "";
        return stationMemberRepository
                .resolveId(stationId, author.memberUid())
                .flatMap(memberId -> stationMemberRepository
                        .findById(memberId)
                        .filter(m -> m.accountId() != null)
                        .flatMap(m -> accountRepository.findById(m.accountId()))
                        .map(Account::fullName))
                .orElse("");
    }

    /**
     * The two halves of the news-views modal: members who have seen the news (with
     * timestamps) and members who have not.
     */
    public record ViewerSummary(List<NewsViewer> seen, List<NewsViewer> unseen) {}
}
