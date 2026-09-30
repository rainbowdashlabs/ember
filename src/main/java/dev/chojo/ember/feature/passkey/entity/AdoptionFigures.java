/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.entity;

/**
 * How far the instance has come towards passkeys.
 *
 * @param accountsWithTriedPasskey accounts holding a passkey that has completed a sign-in
 * @param accountsWithPassword accounts still holding a password
 * @param accountsWithPasswordAndNoPasskey the group that cannot move yet
 */
public record AdoptionFigures(
        int accountsWithTriedPasskey, int accountsWithPassword, int accountsWithPasswordAndNoPasskey) {}
