/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;

/**
 * One permission of a hierarchy as the permission picker draws it.
 *
 * @param name     the permission
 * @param children the permissions it directly implies
 */
public record PermissionNode(String name, List<String> children) {}
