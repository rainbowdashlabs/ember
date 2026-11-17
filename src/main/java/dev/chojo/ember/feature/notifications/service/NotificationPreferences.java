/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.repository.NotificationSettingsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * What somebody asked to be told, and where.
 *
 * <p>Whether a notification is written at all is decided when it is written, in the same statement;
 * this is for the screens and the feeds that show or change the choices. A station membership has a
 * mail switch and a choice per type; a cluster membership has one mail switch for everything the
 * association sends, since its people have no per-type settings.
 */
@Singleton
public class NotificationPreferences {
    private static final Logger log = LoggerFactory.getLogger(NotificationPreferences.class);

    private final NotificationSettingsRepository settingsRepository;
    private final ClusterRepository clusterRepository;
    private final EmailService emailService;
    private final NotificationVisibility visibility;

    @Inject
    public NotificationPreferences(
            NotificationSettingsRepository settingsRepository,
            ClusterRepository clusterRepository,
            EmailService emailService,
            NotificationVisibility visibility) {
        this.visibility = visibility;
        this.settingsRepository = settingsRepository;
        this.clusterRepository = clusterRepository;
        this.emailService = emailService;
    }

    /**
     * A cluster member's delivery settings for the association's notifications.
     *
     * @param emailEnabled  whether they asked for the association's notifications by mail
     * @param mailAvailable whether this installation can send mail at all
     * @param pushEnabled   whether they are also pushed to the member's devices
     */
    public record ClusterMail(boolean emailEnabled, boolean mailAvailable, boolean pushEnabled) {}

    /**
     * A station member's choices per type. A type without an entry keeps the defaults.
     *
     * @param memberId the station member
     * @return their choices by type
     */
    public Map<NotificationType, NotificationSetting> settingsOf(int memberId) {
        return settingsRepository.findByMemberAsMap(memberId);
    }

    /**
     * The types a station's members are offered a choice about: none of a module the station does not
     * have. The choices already made about the others stay stored and count again once it is back.
     *
     * @param stationId the station
     * @return the offered types
     */
    public Set<NotificationType> offeredAt(int stationId) {
        return visibility.offeredAt(stationId);
    }

    /**
     * Stores a station member's choices for the types named; the others keep what they had.
     *
     * @param memberId the station member
     * @param settings their choices by type
     */
    public void updateSettings(int memberId, Map<NotificationType, NotificationSetting> settings) {
        settingsRepository.upsertAll(memberId, settings);
        log.info("Notification settings updated: member={}, count={}", memberId, settings.size());
    }

    /**
     * A cluster member's mail setting, with whether mail can be sent at all.
     *
     * @param clusterMemberId the cluster member
     * @return their setting
     */
    public ClusterMail clusterMailOf(int clusterMemberId) {
        return new ClusterMail(
                clusterRepository.isEmailEnabled(clusterMemberId),
                emailService.canInstanceSend(),
                clusterRepository.isPushEnabled(clusterMemberId));
    }

    /**
     * Switches a cluster member's mail or pushes on or off; what a request leaves out stays as it is.
     *
     * @param clusterMemberId the cluster member
     * @param email           whether they want the association's notifications by mail, null to keep
     * @param push            whether they want them pushed, null to keep
     * @return their setting afterwards
     */
    public ClusterMail setClusterMail(int clusterMemberId, @Nullable Boolean email, @Nullable Boolean push) {
        if (email != null) clusterRepository.setEmailEnabled(clusterMemberId, email);
        if (push != null) clusterRepository.setPushEnabled(clusterMemberId, push);
        return clusterMailOf(clusterMemberId);
    }
}
