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
import dev.chojo.ember.feature.comment.service.MentionLimits;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.content.service.ContentProjection;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.entity.NewsViewer;
import dev.chojo.ember.feature.news.repository.NewsRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Markdown;
import io.javalin.http.BadRequestResponse;
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
    private static final Pattern BULK_MENTION_PATTERN =
            Pattern.compile("@\\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\\d+)]");

    private final NewsRepository newsRepository;
    private final ContentBlockService blocks;
    /**
     * What a system entry is shown as having been written by. The instance is not a member of any
     * station, so there is no identity to resolve and no avatar to draw: the product's own name
     * stands in, and the badge beside it says the rest.
     */
    public static final String SYSTEM_AUTHOR_NAME = "Ember";

    private final StationRepository stationRepository;
    private final RestrictionService restrictionService;
    private final DomainEventBus eventBus;
    private final StationMemberRepository stationMemberRepository;
    private final MemberLookupService memberLookupService;
    private final AccountRepository accountRepository;

    @Inject
    public NewsService(
            NewsRepository newsRepository,
            ContentBlockService blocks,
            StationRepository stationRepository,
            RestrictionService restrictionService,
            DomainEventBus eventBus,
            StationMemberRepository stationMemberRepository,
            MemberLookupService memberLookupService,
            AccountRepository accountRepository) {
        this.newsRepository = newsRepository;
        this.blocks = blocks;
        this.stationRepository = stationRepository;
        this.restrictionService = restrictionService;
        this.eventBus = eventBus;
        this.stationMemberRepository = stationMemberRepository;
        this.memberLookupService = memberLookupService;
        this.accountRepository = accountRepository;
    }

    /**
     * Derives a plain-text preview from a Markdown article body. Strips the most common
     * formatting (headings, emphasis, lists, code fences, links → label) but preserves
     * paragraph structure (single newlines kept, runs of 3+ newlines collapsed to a single
     * paragraph break) so the feed renderer can re-flow it as multi-line HTML. Markdown
     * tables are converted to {@code "col · col · col"} lines so they read as structured
     * key/value pairs instead of dumping raw {@code |} characters. The renderer applies its
     * own length cap on top - we just hand it readable plain text. Returns {@code null}
     * when the input is blank.
     */
    static String previewOf(String markdown) {
        if (markdown == null || markdown.isBlank()) return null;
        String stripped = markdown
                // Fenced code blocks add nothing useful in plain text - drop them entirely.
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
                // Bullet / numbered list markers at line start - keep a bullet glyph so the
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
     * @param author          identity of the author
     * @param groupIds        group IDs to restrict visibility to (empty for unrestricted)
     * @return the newly created news entry
     */
    public News create(
            int stationId,
            String title,
            String contentMarkdown,
            MemberIdentity author,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        var news = newsRepository.create(stationId, title, contentMarkdown, Markdown.toHtml(contentMarkdown), author);
        setRestrictions(news.id(), new RestrictionSelection(userTypes, groupIds, tagIds, memberIds, null));
        String authorName = resolveAuthorName(stationId, author);
        eventBus.publish(new NewsCreated(stationId, news.id(), title, authorName, previewOf(contentMarkdown)));
        log.info("Created news {} on station {}", news.id(), stationId);
        return news;
    }

    /**
     * Publishes an entry from the instance to every station at once.
     *
     * <p>It carries no author and no station: it is shown as coming from the instance itself. The
     * restrictions it takes are user types alone, because groups, tags and single members are
     * things one station has and the entry is read in all of them.
     *
     * <p>Notifying is asked for rather than assumed. Most of what an instance has to say is a
     * notice people meet when they next look, and waking every member of every station for it would
     * teach them to ignore the ones that matter.
     *
     * @param title           entry title
     * @param contentMarkdown entry body in Markdown
     * @param userTypes       the user types that may read it, or empty for everyone
     * @param publish         whether it is published straight away
     * @param notify          whether members are notified of it
     * @return the newly created entry
     */
    public News createSystem(
            String title, String contentMarkdown, List<StationUserType> userTypes, boolean publish, boolean notify) {
        var news = newsRepository.createSystem(title, contentMarkdown, Markdown.toHtml(contentMarkdown), publish);
        setRestrictions(news.id(), new RestrictionSelection(userTypes, List.of(), List.of(), List.of(), null));
        if (publish && notify) {
            notifySystemEntry(news, title, contentMarkdown);
        }
        log.info("Created system news {}", news.id());
        return news;
    }

    /**
     * Tells every station about a system entry, one station at a time, because that is what a
     * notification is addressed to. The entry itself is still the single row they all read.
     */
    private void notifySystemEntry(News news, String title, String contentMarkdown) {
        for (var station : stationRepository.findAll()) {
            eventBus.publish(
                    new NewsCreated(station.id(), news.id(), title, SYSTEM_AUTHOR_NAME, previewOf(contentMarkdown)));
        }
    }

    /**
     * The entries the instance has published, newest first.
     *
     * @param offset pagination offset
     * @param limit  maximum number of results
     * @return the system entries
     */
    public List<News> findSystem(int offset, int limit) {
        return newsRepository.findSystem(offset, limit);
    }

    /**
     * Whether one member may read one entry, restrictions and all.
     *
     * @param newsId   the news article ID
     * @param memberId the member reading it
     * @return {@code true} if the entry is visible to that member
     */
    public boolean isVisibleForMember(int newsId, int memberId) {
        return newsRepository.isVisibleForMember(newsId, memberId);
    }

    /**
     * The comments on an entry that were written from one station. A station reads its own part of
     * the conversation under a system entry; the instance reads all of it.
     *
     * @param newsId     the news article ID
     * @param stationUid the station whose comments to return
     * @return the comments written from that station
     */
    public List<NewsComment> findCommentsForStation(int newsId, UUID stationUid) {
        return newsRepository.findCommentsByNewsForStation(newsId, stationUid);
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
     * <p>The HTML is rendered here from the Markdown rather than taken from whoever asked for the
     * change. A browser's rendering is a convenience, not evidence: the stored HTML is served back
     * to every reader as markup, so it has to come from a renderer this application controls and a
     * sanitiser it trusts.
     *
     * @param id              the news article ID
     * @param title           new title
     * @param contentMarkdown new Markdown content
     * @param groupIds        new group restriction IDs
     * @return the updated news article, or empty if the article was not found
     */
    public Optional<News> update(
            int id,
            String title,
            String contentMarkdown,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        if (newsRepository.update(id, title, contentMarkdown, Markdown.toHtml(contentMarkdown))) {
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

    // --- Blocks ---

    /**
     * Turns a plain entry into one built from blocks, putting what the author already wrote into a
     * single markdown block. Nothing is parsed and nothing is lost.
     *
     * <p>The switch is one way. An author who wants the plain editor back copies the text into a
     * new entry, which keeps the stored text of a rich entry derived: there is no path where
     * somebody edits the projection and expects the blocks to follow.
     */
    public Optional<News> switchToRich(int id) {
        var news = newsRepository.findById(id).orElse(null);
        if (news == null) return Optional.empty();
        if (news.contentMode() == ContentMode.RICH) return Optional.of(news);

        // A system entry belongs to no station, and neither do its blocks: it is read in every
        // station, so a container hanging off one of them would be the wrong owner and, since no
        // station carries the id a system entry reads as, no owner at all.
        var container = blocks.create(news.systemEntry() ? null : news.stationId());
        String existing = news.contentMarkdown() == null ? "" : news.contentMarkdown();
        if (!existing.isBlank()) {
            blocks.save(
                    container.id(),
                    List.of(new ContentBlockService.RowData(
                            0,
                            List.of(new ContentBlockService.CellData(
                                    0, 100.0, CellContentType.MARKDOWN, existing, CellConfig.EMPTY)))),
                    ContentBlockService.Scope.ARTICLE);
        }
        if (!newsRepository.setRichMode(id, container.id())) {
            blocks.delete(container.id());
            return Optional.empty();
        }
        log.info("News {} switched to rich mode with container {}", id, container.id());
        return newsRepository.findById(id);
    }

    /**
     * The blocks of a rich entry, in reading order.
     */
    public List<ContentRow> loadBlocks(News news) {
        if (news.containerId() == null) return List.of();
        return blocks.loadRows(news.containerId());
    }

    /**
     * Saves the blocks of a rich entry and rewrites the stored text from them.
     *
     * <p>The projection runs on every save, including a save that only reorders blocks: a stale
     * projection means a stale search summary, a stale notification preview and a stale feed.
     */
    public Optional<News> saveBlocks(int id, List<ContentBlockService.RowData> rows) {
        var news = newsRepository.findById(id).orElse(null);
        if (news == null) return Optional.empty();
        if (news.contentMode() != ContentMode.RICH || news.containerId() == null) {
            throw new BadRequestResponse("This entry is not built from blocks");
        }

        blocks.save(news.containerId(), rows, ContentBlockService.Scope.ARTICLE);

        // The pictures of a system entry come out of the instance library, which is addressed by
        // the literal scope rather than through a station: the entry is read in stations that hold
        // no copy of the file.
        String mediaScope = news.systemEntry()
                ? MediaLibraryService.INSTANCE_SCOPE
                : String.valueOf(stationRepository.resolveUid(news.stationId()));
        String markdown = ContentProjection.toMarkdown(
                blocks.loadRows(news.containerId()), hash -> "/api/v1/public/media/" + mediaScope + "/" + hash);
        newsRepository.update(id, news.title(), markdown, Markdown.toHtml(markdown));
        log.info("News {} blocks saved and projected ({} rows)", id, rows.size());
        return newsRepository.findById(id);
    }

    public boolean delete(int id) {
        var news = newsRepository.findById(id).orElse(null);
        if (news == null) {
            log.warn("Delete for news {} skipped: not found", id);
            return false;
        }
        if (newsRepository.delete(id)) {
            // The container is the owned side, so nothing cleans it up for us.
            blocks.delete(news.containerId());
            eventBus.publish(new NewsDeleted(news.stationId(), id, news.title()));
            log.info("Deleted news {} on station {}", id, news.stationId());
            return true;
        }
        log.warn("Delete for news {} affected zero rows", id);
        return false;
    }

    /**
     * Records that a member fully saw a news entry in their viewport. Idempotent -
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
                    parentAuthorMemberId = memberLookupService
                            .resolveId(stationId, parentComment.author().memberUid())
                            .orElse(null);
                }
            }
            Integer authorMemberId = author != null
                    ? memberLookupService
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
                int mentioned = 0;
                while (matcher.find() && mentioned++ < MentionLimits.MAX_MEMBER_MENTIONS) {
                    try {
                        var memberUid = UUID.fromString(matcher.group(2));
                        memberLookupService.resolveId(stationId, memberUid).ifPresent(mentionedId -> {
                            if (!mentionedId.equals(authorMemberId)) {
                                eventBus.publish(new MentionedInComment(
                                        stationId,
                                        mentionedId,
                                        authorMemberId,
                                        authorName,
                                        CommentEntityType.NEWS,
                                        newsId,
                                        news.title(),
                                        comment.id(),
                                        preview));
                            }
                        });
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                var bulkMatcher = BULK_MENTION_PATTERN.matcher(content);
                int addressed = 0;
                while (bulkMatcher.find() && addressed++ < MentionLimits.MAX_BULK_MENTIONS) {
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
                            comment.id(),
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
            eventBus.publish(new CommentDeleted(stationId, CommentEntityType.NEWS, id));
            log.info("Deleted news comment {} on station {}", id, stationId);
            return true;
        }
        log.warn("Delete for news comment {} affected zero rows", id);
        return false;
    }

    private String resolveAuthorName(int stationId, MemberIdentity author) {
        if (author == null) return "";
        return memberLookupService
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
