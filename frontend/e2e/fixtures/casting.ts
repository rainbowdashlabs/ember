/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext} from '@playwright/test'
import {demoAccounts, instanceAdmin, stationPeers, type DemoAccount} from './auth'
import type {Cast, CastMember} from './cast'

/** A membership at the seeded station, which is where the ids come from. */
interface MemberRow {
    id: number
    accountId: number
    email?: string
}

/**
 * Settles who the run acts as, once, before any story has changed anything.
 *
 * <p>The addresses are read here and turned into ids here, which is the only moment either is safe:
 * the seeded accounts still carry the addresses they were seeded with, and the parts they are cast
 * in cannot move afterwards. Anything resolved later reads a station some story has since edited.
 *
 * @param request    an unauthenticated context, for the demo listings
 * @param managers   a context signed in as the station's manager, for the membership ids
 * @param asGuardian opens a context as the named guardian, which is the only way to read whom they
 *     look after: that listing answers about whoever is asking and about nobody else
 */
export async function settleCast(
    request: APIRequestContext,
    managers: APIRequestContext,
    asGuardian: (email: string, stationId?: string) => Promise<APIRequestContext>,
): Promise<Cast> {
    const accounts = await demoAccounts(request)
    const {manager, member} = await stationPeers(request)
    const admin = await instanceAdmin(request)

    const listed = await managers.get('/api/v1/station-members')
    if (!listed.ok()) throw new Error(`The member list answered ${listed.status()} while casting`)
    const rows = await listed.json() as MemberRow[]
    const byEmail = new Map(rows.filter(row => !!row.email).map(row => [row.email!, row]))

    const part = (account: DemoAccount, name: string): CastMember => {
        const row = byEmail.get(account.email)
        return {
            accountId: row?.accountId ?? 0,
            memberId: row?.id ?? 0,
            email: account.email,
            firstName: account.firstName,
            lastName: account.lastName,
            stationId: account.stationId,
            part: name,
        }
    }

    /**
     * Whom a guardian looks after, asked as that guardian because nobody else may ask.
     *
     * <p>Loud when it cannot be asked. Answering "nobody" on a refusal would quietly cast their
     * charges in other parts, which is the very collision this is here to prevent, and the run
     * would fail much later somewhere else.
     */
    const chargesOf = async (guardian: DemoAccount): Promise<number[]> => {
        const context = await asGuardian(guardian.email, guardian.stationId)
        try {
            const listed = await context.get('/api/v1/managed-members')
            if (!listed.ok()) {
                throw new Error(`Casting could not read the charges of ${guardian.email}: ${listed.status()}`)
            }
            return (await listed.json() as {id: number}[]).map(row => row.id)
        } finally {
            await context.dispose()
        }
    }

    const ofStation = (candidate: DemoAccount) => candidate.stationId === manager.stationId
    const guardianPool = accounts.filter(candidate =>
        ofStation(candidate) && !!candidate.email && candidate.permissions.includes('MEMBER_GUARDIAN'))
    const guardianNames = ['guardianSpec', 'sidebarSpec', 'passkeySpec'] as const
    if (guardianPool.length < guardianNames.length) {
        throw new Error(`The station seeds ${guardianPool.length} guardians; ${guardianNames.length} parts need one`)
    }

    // The people the cast guardians look after are spoken for too. A guardian story hands a charge
    // a new address, which ends that charge's sessions and leaves the address the cast was written
    // under finding nobody: a charge that was also a passkey part failed its story on a login the
    // seed had answered a moment earlier. Excluding the guardians themselves was not enough.
    const charges = new Set<number>()
    for (const guardian of guardianPool.slice(0, guardianNames.length)) {
        for (const id of await chargesOf(guardian)) charges.add(id)
    }

    // Ordinary members, in the order their ids were handed out: an order the stories cannot move,
    // unlike the address they used to be sorted by. The shared member and the guardians stand out,
    // because a story that ends one of these sessions on purpose must not end a session anybody
    // else is holding.
    const spokenFor = new Set([manager.email, member.email, admin.email, ...guardianPool.map(g => g.email)]);
    const slotPool = accounts
        .filter(candidate =>
            ofStation(candidate)
            && !!candidate.email
            && candidate.userType === 'MEMBER'
            && !spokenFor.has(candidate.email)
            && !candidate.permissions.includes('STATION_MANAGER'))
        .map(candidate => part(candidate, 'passkeySlot'))
        .filter(cast => cast.memberId > 0 && !charges.has(cast.memberId))
        .sort((a, b) => a.memberId - b.memberId)

    const PASSKEY_PARTS = 6
    if (slotPool.length < PASSKEY_PARTS) {
        throw new Error(`The station seeds ${slotPool.length} spare members; the passkey stories need ${PASSKEY_PARTS}`)
    }

    // Nothing beyond the ordinary, for the stories that prove a refusal. The list is the union of
    // what those stories ask to be missing, so one person answers for all of them.
    const NOTHING_BEYOND_THE_ORDINARY = [
        'STATION_ADMINISTRATOR', 'STATION_MANAGER', 'INVENTORY_EDIT',
        'INVENTORY_CREATE_EXTERNAL', 'INVENTORY_MANAGER', 'MEMBER_MANAGER', 'MEMBER_NOTES',
    ]
    const plainMember = accounts.find(candidate =>
        ofStation(candidate) && !!candidate.email && candidate.userType === 'MEMBER'
        && NOTHING_BEYOND_THE_ORDINARY.every(right => !candidate.permissions.includes(right)))
    if (!plainMember) throw new Error('The station seeds no member free of every management right')

    const plainTeam = accounts.find(candidate =>
        ofStation(candidate) && !!candidate.email && candidate.userType === 'TEAM'
        && !candidate.permissions.includes('MEMBER_NOTES')
        && !candidate.permissions.includes('MEMBER_MANAGER'))
    if (!plainTeam) throw new Error('The station seeds no team member free of the member rights')

    const administrator = accounts.find(candidate =>
        !!candidate.email && candidate.permissions.includes('STATION_ADMINISTRATOR'))
    if (!administrator) throw new Error('No seeded account administers a station')

    // Of the second seeded station, so that signing out cannot reach anybody the rest of the run is
    // acting as. The suffix is what tells the two seeds' people apart.
    const loner = accounts.find(candidate =>
        candidate.userType === 'MEMBER' && !!candidate.email && candidate.email.endsWith('.nord.local'))
    if (!loner) throw new Error('The second seeded station carries no ordinary member to sign out')

    return {
        manager: part(manager, 'manager'),
        member: part(member, 'member'),
        admin: part(admin, 'admin'),
        guardians: {
            guardianSpec: part(guardianPool[0]!, 'guardianSpec'),
            sidebarSpec: part(guardianPool[1]!, 'sidebarSpec'),
            passkeySpec: part(guardianPool[2]!, 'passkeySpec'),
        },
        passkeySlots: slotPool.slice(0, PASSKEY_PARTS),
        logoutLoner: {...part(loner, 'logoutLoner'), memberId: 0},
        administrator: part(administrator, 'administrator'),
        plainMember: part(plainMember, 'plainMember'),
        plainTeam: part(plainTeam, 'plainTeam'),
    }
}
