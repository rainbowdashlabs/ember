/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {LinkOrigin, type LinkPrompt} from '@/api/generated/schema'

/** Whether an association asks the account to take a role, rather than a station to link it to a member. */
export function isAssociationRequest(prompt: LinkPrompt): boolean {
    return prompt.origin === LinkOrigin.ASSOCIATION_INVITE
}

/** Who asks: the association or the station, by its name. */
export function askerOf(prompt: LinkPrompt): string {
    return (isAssociationRequest(prompt) ? prompt.associationName : prompt.stationName) ?? ''
}

/** The message key that confirms an answer, which says what the answer did for this kind of request. */
export function answeredKey(prompt: LinkPrompt, accepting: boolean): string {
    if (isAssociationRequest(prompt)) return accepting ? 'accountLinks.roleAccepted' : 'accountLinks.roleDeclined'
    return accepting ? 'accountLinks.accepted' : 'accountLinks.declined'
}
