/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * One live thing standing in the way of an inventory changing what kind of thing it holds.
 *
 * <p>The refusal carries these rather than a count, because "three requirements are in the way"
 * sends somebody hunting. The identifier is what lets the screen link straight to the thing, and
 * the label is what it is called there, so the person reading the refusal can go and deal with it.
 *
 * @param kind  what sort of thing it is
 * @param id    its identifier, so the screen showing the refusal can reach it
 * @param label what it is called, in the words the screen it lives on uses
 */
public record SwitchBlocker(SwitchBlockerKind kind, int id, String label) {}
