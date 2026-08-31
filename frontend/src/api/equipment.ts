/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** A day either way, which is the ordinary case and what a new line starts with. */
export const DEFAULT_LEAD_MINUTES = 24 * 60

/**
 * One line of what an appointment needs. A line naming a piece carries an itemId, a line asking for
 * a count of one kind carries an artId, and a line counting out of a whole inventory carries an
 * inventoryId. Exactly one of the three.
 */
export interface EquipmentNeed {
    id: number
    eventId: number
    /** Null where the line holds for the whole series, set where one evening says something of its own. */
    eventDate: string | null
    itemId: number | null
    artId: number | null
    inventoryId: number | null
    quantity: number
    leadMinutes: number
    trailMinutes: number
    position: number
}

/** What already holds some of the stock a line asks for, over that line's window. */
export interface EquipmentClaim {
    origin: 'OWN_NEED' | 'LOAN' | 'BLOCK'
    label: string
    eventId: number | null
    eventDate: string | null
    quantity: number
    from: string
    to: string
    firm: boolean
}

/** One line of an appointment's needs, answered for one evening. */
export interface NeedCoverage {
    need: EquipmentNeed
    label: string
    from: string
    to: string
    own: number
    borrowed: number
    outstanding: number
    stock: number
    claimed: number
    overClaim: EquipmentClaim[]
    missing: number
    covered: boolean
}

/** A piece that went out for one evening. */
export interface EquipmentHandover {
    id: number
    needId: number
    eventDate: string
    itemId: number
    claimFrom: string
    claimTo: string
    handedBy: number | null
    handedAt: string
    returnedAt: string | null
    outstanding: boolean
}

/** A piece that goes with one somebody has just picked. */
export interface EquipmentRecommendation {
    itemId: number
    itemName: string
    internalId: string
    inventoryId: number
    inventoryName: string
    artId: number | null
    artName: string
    /** True where a shared word found it rather than the shelf it stands on. */
    byWord: boolean
}

/** One line of a list somebody is collecting from the partner stations. */
export interface CollectedLine {
    owningStationId: number
    inventoryId: number
    artId: number | null
    quantity: number
    needId: number | null
}

export interface LineCheck {
    line: CollectedLine
    available: number
    changed: boolean
}

export interface CollectedCheck {
    lines: LineCheck[]
    stationsInvolved: number[]
}

export interface NeedPayload {
    itemId?: number | null
    artId?: number | null
    inventoryId?: number | null
    quantity?: number
    leadMinutes?: number
    trailMinutes?: number
    eventDate?: string | null
}

export async function list(eventId: number): Promise<EquipmentNeed[]> {
    const res = await client.get<EquipmentNeed[]>(`/events/${eventId}/equipment`)
    return res.data
}

export async function coverage(eventId: number, date: string): Promise<NeedCoverage[]> {
    const res = await client.get<NeedCoverage[]>(`/events/${eventId}/equipment/coverage`, {params: {date}})
    return res.data
}

export async function add(eventId: number, payload: NeedPayload): Promise<EquipmentNeed> {
    const res = await client.post<EquipmentNeed>(`/events/${eventId}/equipment`, payload)
    return res.data
}

export async function update(
    eventId: number,
    needId: number,
    payload: {quantity?: number; leadMinutes?: number; trailMinutes?: number},
): Promise<void> {
    await client.put(`/events/${eventId}/equipment/${needId}`, payload)
}

export async function reorder(eventId: number, needIds: number[]): Promise<void> {
    await client.put(`/events/${eventId}/equipment/order`, {needIds})
}

export async function remove(eventId: number, needId: number): Promise<void> {
    await client.delete(`/events/${eventId}/equipment/${needId}`)
}

export async function handovers(eventId: number, date: string): Promise<EquipmentHandover[]> {
    const res = await client.get<EquipmentHandover[]>(`/events/${eventId}/equipment/handovers`, {params: {date}})
    return res.data
}

export async function handOver(
    eventId: number,
    needId: number,
    date: string,
    itemIds: number[],
): Promise<EquipmentHandover[]> {
    const res = await client.post<EquipmentHandover[]>(
        `/events/${eventId}/equipment/${needId}/handovers`,
        {itemIds},
        {params: {date}},
    )
    return res.data
}

export async function handBack(eventId: number, handoverId: number): Promise<void> {
    await client.delete(`/events/${eventId}/equipment/handovers/${handoverId}`)
}

export async function recommendations(itemId: number): Promise<EquipmentRecommendation[]> {
    const res = await client.get<EquipmentRecommendation[]>('/equipment/recommendations', {params: {itemId}})
    return res.data
}

/**
 * Counts a collected list again against what the partners have free now, and says how many requests
 * it will turn into.
 */
export async function checkCollected(from: string, to: string, lines: CollectedLine[]): Promise<CollectedCheck> {
    const res = await client.post<CollectedCheck>('/equipment/collected', {from, to, lines})
    return res.data
}
