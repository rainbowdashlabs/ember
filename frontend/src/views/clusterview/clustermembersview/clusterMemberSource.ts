/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {clusterMembers} from '@/api'
import type {ManagedMember} from '@/api/clusterMembers'
import type {RichMember} from '@/api/stationMembers'
import type {MemberDataSource} from '@/views/stationview/members/listview/useMemberData'

/**
 * How many people the association's list will pull in before it stops asking.
 *
 * <p>The table filters and sorts what it holds, so it has to hold the whole roll to answer honestly.
 * The search is paged, so its pages are walked until they run out or this many have been read. An
 * association past this size needs the filtering and sorting done by the server instead, and the
 * screen says so rather than quietly showing a slice.
 */
export const MANAGED_MEMBER_CAP = 2000

const PAGE_SIZE = 200

/**
 * A person at one of the association's stations, in the shape the station's own list speaks.
 *
 * <p>What the search returns and what the table reads do not line up everywhere, and the gaps here are
 * honest rather than filler. No answers to profile questions, no groups and no tags travel with it,
 * because those belong to the station that holds them and the search does not reach for them.
 *
 * <p>The identity is not a gap of that kind. Every row draws its person through it, so passing null left
 * an empty span beside an empty avatar on every line of the list, with the name sitting unread in the
 * field beside it.
 */
function toRich(member: ManagedMember): RichMember {
    return {
        id: member.id,
        stationId: 0,
        accountId: null,
        name: member.name,
        firstName: member.name,
        lastName: '',
        email: member.email,
        accountSetupPending: false,
        setupMailExpiresAt: null,
        mailReaches: 'SELF' as const,
        former: member.former,
        userType: member.userType,
        roles: [],
        groups: [],
        tags: [],
        profileValues: {},
        identity: member.identity,
    }
}

/**
 * The people at every station the association governs.
 *
 * <p>Hands back the source the list reads, and beside it what the search knows that a station's own
 * list has no column for: which station somebody belongs to, and whether they own it.
 *
 * @param includeFormer whether people who have left are read as well
 */
export function useClusterMemberSource(includeFormer: () => boolean) {
    /** The search results as they came, keyed by member, for what the table cannot carry. */
    const managed = ref<Map<number, ManagedMember>>(new Map())
    /** True when the last load stopped at the cap rather than at the end of the roll. */
    const overflowed = ref(false)

    const source: MemberDataSource = {
        load: async () => {
            const collected: ManagedMember[] = []
            overflowed.value = false
            for (let page = 0; ; page++) {
                const result = await clusterMembers.searchManagedMembers({
                    includeFormer: includeFormer(),
                    page,
                    size: PAGE_SIZE,
                })
                collected.push(...result.members)
                if (result.members.length === 0 || collected.length >= result.total) break
                if (collected.length >= MANAGED_MEMBER_CAP) {
                    overflowed.value = collected.length < result.total
                    break
                }
            }
            managed.value = new Map(collected.map(m => [m.id, m]))
            // No profile questions travel with the search, so none can be offered as a column. The
            // association's own questions are asked of these people and belong here; the search would
            // have to carry their answers first.
            return {members: collected.map(toRich), fields: [], roles: []}
        },
    }

    return {source, managed, overflowed}
}
