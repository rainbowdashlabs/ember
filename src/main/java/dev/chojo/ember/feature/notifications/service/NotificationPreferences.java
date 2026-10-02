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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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

    @Inject
    public NotificationPreferences(
            NotificationSettingsRepository settingsRepository,
            ClusterRepository clusterRepository,
            EmailService emailService) {
        this.settingsRepository = settingsRepository;
        this.clusterRepository = clusterRepository;
        this.emailService = emailService;
    }

    /**
     * A cluster member's mail setting.
     *
     * @param emailEnabled  whether they asked for the association's notifications by mail
     * @param mailAvailable whether this installation can send mail at all
     */
    public record ClusterMail(boolean emailEnabled, boolean mailAvailable) {}

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
        return new ClusterMail(clusterRepository.isEmailEnabled(clusterMemberId), emailService.canInstanceSend());
    }

    /**
     * Switches a cluster member's mail on or off.
     *
     * @param clusterMemberId the cluster member
     * @param enabled         whether they want the association's notifications by mail
     * @return their setting afterwards
     */
    public ClusterMail setClusterMail(int clusterMemberId, boolean enabled) {
        clusterRepository.setEmailEnabled(clusterMemberId, enabled);
        return clusterMailOf(clusterMemberId);
    }
}
