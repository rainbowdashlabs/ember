/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readFile} from 'node:fs/promises'

/**
 * Who the run acts as, settled once before any story starts.
 *
 * <p>Every part a story needs is resolved at global setup and written down here, named by the ids
 * the database gave it. A spec reads the part it was cast in; nothing works out for itself who to
 * act as, and nothing recomputes it while the run is going.
 *
 * <p>That is the whole point. A part used to be found by asking for the first account matching a
 * description, and a description is the same answer in every worker: four of them converged on one
 * person, and the helper promising "an account nobody else is using" took a list of addresses to
 * avoid that almost every caller left empty. Worse, the descriptions read the address, and stories
 * rewrite addresses. A pool filtered and sorted by it lost a member the moment one was given a new
 * one and handed every part below it to somebody else, mid-run, in another worker.
 *
 * <p>So a part is an id. Ids are what the database keeps and what no story rewrites.
 */
export interface CastMember {
    /** The account, which is what a login names. */
    accountId: number
    /** The membership at the seeded station, which is what the station's own screens name. */
    memberId: number
    /**
     * The address as it stood at setup. Good for logging in and for nothing else: a story may give
     * this person another one, and then this no longer finds them.
     */
    email: string
    stationId?: string
    /** What they were cast for, so a failure says which part is missing rather than which id. */
    part: string
}

export interface Cast {
    /** The three sessions the suite shares, logged in once and read by the page fixtures. */
    manager: CastMember
    member: CastMember
    admin: CastMember
    /**
     * A guardian each for the specs that need one. They used to share the first guardian the demo
     * lists, concurrently, and a guardian speaks for somebody: two stories doing that at once
     * settle it between them.
     */
    guardians: Record<'guardianSpec' | 'sidebarSpec' | 'passkeySpec', CastMember>
    /**
     * The members the passkey stories act as, one apiece. Several of them end a session or refuse a
     * password on purpose, so no two may be the same person.
     */
    passkeySlots: CastMember[]
    /** Somebody to sign out without taking the suite's own session with it. */
    logoutLoner: CastMember
}

/** Where global setup leaves the cast, beside the sessions it logs in. */
export function castPath(): string {
    return 'e2e/.auth/cast.json'
}

const CAST_PATH = castPath()

let loaded: Cast | null = null

/** The cast for this run, read from disk once per worker. */
export async function cast(): Promise<Cast> {
    if (loaded) return loaded
    try {
        loaded = JSON.parse(await readFile(CAST_PATH, 'utf-8')) as Cast
    } catch (cause) {
        throw new Error(`The cast has not been settled; global setup writes ${CAST_PATH}`, {cause})
    }
    return loaded
}

/**
 * Every membership the cast holds, for a story that must not touch anybody else's part.
 *
 * <p>Ids rather than addresses, because a story asking this is usually about to write an address.
 */
export async function spokenForMemberIds(): Promise<Set<number>> {
    const settled = await cast()
    const everyone = [
        settled.manager,
        settled.member,
        settled.admin,
        ...Object.values(settled.guardians),
        ...settled.passkeySlots,
    ]
    return new Set(everyone.map(one => one.memberId).filter(id => id > 0))
}

/** The member the passkey stories act as in the given part. */
export async function passkeySlot(slot: number): Promise<CastMember> {
    const slots = (await cast()).passkeySlots
    const member = slots[slot]
    if (!member) throw new Error(`The cast holds ${slots.length} passkey parts, so there is no ${slot}`)
    return member
}
