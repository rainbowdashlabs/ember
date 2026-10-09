/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * Who a station's mail says it comes from when the instance carries it: the station by name, with
 * replies going where the station said.
 *
 * @param name    the station's name, shown beside the instance's sender address
 * @param replyTo where replies to the station's mail go, empty where the station named nothing
 */
public record StationMailSender(String name, String replyTo) {}
