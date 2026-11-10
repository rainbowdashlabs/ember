/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {stationMembers} from '@/api'
import type {MemberCompletion} from '@/api/generated/schema'
import {audienceLists, type AudienceLists} from '@/components/documents/generation'

/** Whom the managing party of a procedure can name: the station's groups, tags and members. */
export type PartyChoices = AudienceLists & {members: MemberCompletion[]}

/** No choices yet, before a screen has read them. */
export function noPartyChoices(): PartyChoices {
    return {members: [], groups: [], tags: []}
}

/**
 * Reads whom a party can name. Groups and tags the reader may not read come back empty, as for every
 * audience; the members are readable by everybody signed in, so a failure to read them is a failure of
 * the screen and reaches the caller.
 */
export async function loadPartyChoices(): Promise<PartyChoices> {
    const [members, lists] = await Promise.all([stationMembers.listCompletions(), audienceLists()])
    return {...lists, members}
}
