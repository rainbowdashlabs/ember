/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.refusal.MailRefusal;
import dev.chojo.ember.feature.mail.entity.FailedMail;
import dev.chojo.ember.feature.mail.entity.StationFailedMails;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The mails that did not arrive, for the instance's administrator: read, taken note of, and the
 * instance's own sent again.
 *
 * <p>Taking note does not delete anything. It ends the warning for a mail, and the mail stays in the
 * log until the usual cleanup removes it. The instance's own mails are shown in full; a station's are
 * only counted per station, since whom a station writes to and what about is the station's business,
 * and the station keeps its full list on its own mail page, which taking note here does not touch.
 */
@Singleton
public class FailedMailService {

    /** How many failed mails a page holds when the caller does not say. */
    public static final int DEFAULT_SIZE = 25;

    /** How many failed mails a page may hold at most. */
    public static final int MAX_SIZE = 100;

    private final EmailQueueRepository queue;
    private final StationRepository stations;

    @Inject
    public FailedMailService(EmailQueueRepository queue, StationRepository stations) {
        this.queue = queue;
        this.stations = stations;
    }

    /**
     * One page of the instance's own mails that did not arrive and nobody has taken note of, the
     * latest failure first. A page or size out of range is brought back into it rather than refused.
     *
     * @param page the page, counted from zero
     * @param size how many mails a page holds
     */
    public FailedMailPage instanceMails(int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_SIZE);
        int pageIndex = Math.max(page, 0);
        return new FailedMailPage(
                queue.unacknowledgedInstanceFailures(pageSize, pageIndex * pageSize),
                queue.countUnacknowledgedInstanceFailures(),
                pageIndex,
                pageSize);
    }

    /**
     * @return the stations with mails that did not arrive and nobody here has taken note of
     */
    public List<StationFailedMails> stationMails() {
        return queue.unacknowledgedStationFailures();
    }

    /**
     * @return how many mails of the instance and every station did not arrive and nobody has taken
     *         note of, which is what the overview and the sidebar count
     */
    public int unacknowledgedCount() {
        return queue.countUnacknowledgedFailures();
    }

    /**
     * Takes note of one of the instance's own failed mails.
     *
     * @param id the mail
     * @param by the administrator taking note
     */
    public void acknowledge(int id, int by) {
        if (!queue.acknowledgeInstanceFailure(id, by)) throw MailRefusal.FAILED_MAIL_NOT_HERE.raise();
    }

    /**
     * Takes note of all the instance's own failed mails up to the latest failure the administrator
     * saw, so a failure that came in meanwhile still shows.
     *
     * @param upTo the latest failure seen, as the list gave it
     * @param by   the administrator taking note
     */
    public AcknowledgedMails acknowledgeAll(@Nullable Instant upTo, int by) {
        if (upTo == null) throw MailRefusal.FAILED_MAILS_UP_TO_MISSING.raise();
        return new AcknowledgedMails(queue.acknowledgeInstanceFailures(upTo, by));
    }

    /**
     * Takes note of one station's failed mails up to the latest failure the administrator saw, so a
     * failure that came in meanwhile still shows.
     *
     * @param stationUid the station
     * @param upTo       the latest failure seen, as the station list gave it
     * @param by         the administrator taking note
     */
    public AcknowledgedMails acknowledgeStation(UUID stationUid, @Nullable Instant upTo, int by) {
        if (upTo == null) throw MailRefusal.FAILED_MAILS_UP_TO_MISSING.raise();
        var station = stations.findByUid(stationUid).orElseThrow(MailRefusal.STATION_NOT_HERE_FOR_FAILED_MAILS::raise);
        return new AcknowledgedMails(queue.acknowledgeStationFailures(station.id(), upTo, by));
    }

    /**
     * Puts one of the instance's own failed mails back in the queue, at the first provider. Only a
     * mail the queue gave up on goes back: one a provider reported as refused would be refused again.
     * A mail somebody else sent again in the meantime is no longer a failed mail and is refused as
     * not found.
     *
     * @param id the mail
     */
    public void requeue(int id) {
        FailedMail mail = queue.findInstanceFailure(id).orElseThrow(MailRefusal.FAILED_MAIL_NOT_HERE::raise);
        if (!mail.cause().givenUp()) throw MailRefusal.FAILED_MAIL_REFUSED_BY_RECIPIENT.raise();
        if (!mail.requeueable()) throw MailRefusal.FAILED_MAIL_REASON_NOT_RECORDED.raise();
        if (!queue.requeueInstanceFailure(id)) throw MailRefusal.FAILED_MAIL_NOT_HERE.raise();
    }

    /**
     * One page of the instance's own failed mails.
     *
     * @param mails the mails on it
     * @param total how many there are in all
     * @param page  which page it is, counted from zero
     * @param size  how many mails a page holds
     */
    public record FailedMailPage(List<FailedMail> mails, int total, int page, int size) {}

    /**
     * @param acknowledged how many mails were taken note of
     */
    public record AcknowledgedMails(int acknowledged) {}
}
