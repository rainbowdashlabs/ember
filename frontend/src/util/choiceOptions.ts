/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The choices worth keeping out of a list somebody typed.
 *
 * <p>A row added and left empty is a choice with no words, which reaches a dropdown as a blank line
 * nobody can pick on purpose. The editor lets such a row stand while it is being filled in, so the
 * emptying belongs on the way out rather than under the reader's hands.
 *
 * <p>Every feature that stores a list of choices asks here, because this used to be done by whoever
 * remembered to, and the two that forgot are how blank choices got saved at all.
 */
export function usableOptions(options: string[]): string[] {
    return options.map(option => option.trim()).filter(option => option.length > 0)
}
