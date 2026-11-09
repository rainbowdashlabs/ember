/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberCompletion, MemberGroup, UserTag} from '@/api/generated/schema'

/** Whom the managing party of procedures made from sign-ups can name. */
export interface PartyChoices {
    members: MemberCompletion[]
    groups: MemberGroup[]
    tags: UserTag[]
}

/** No choices yet, before the dialog has read them. */
export function noPartyChoices(): PartyChoices {
    return {members: [], groups: [], tags: []}
}
