/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    CollectedLine,
    EquipmentChoices,
    EquipmentHandover,
    EquipmentNeed,
    NeedCoverage,
    NeedRequest,
    NeedUpdate,
    Recommendation,
    RecheckResponse,
} from './generated/schema'

/** A day either way, which is the ordinary case and what a new line starts with. */
export const DEFAULT_LEAD_MINUTES = 24 * 60

export async function list(eventId: number): Promise<EquipmentNeed[]> {
    const res = await client.get<EquipmentNeed[]>(`/events/${eventId}/equipment`)
    return res.data
}

export async function coverage(eventId: number, date: string): Promise<NeedCoverage[]> {
    const res = await client.get<NeedCoverage[]>(`/events/${eventId}/equipment/coverage`, {params: {date}})
    return res.data
}

/** What a line of the appointment can ask for, readable by whoever may edit the appointment. */
export async function choices(eventId: number): Promise<EquipmentChoices> {
    const res = await client.get<EquipmentChoices>(`/events/${eventId}/equipment/choices`)
    return res.data
}

export async function add(eventId: number, payload: NeedRequest): Promise<EquipmentNeed> {
    const res = await client.post<EquipmentNeed>(`/events/${eventId}/equipment`, payload)
    return res.data
}

export async function update(eventId: number, needId: number, payload: NeedUpdate): Promise<void> {
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

export async function recommendations(itemId: number): Promise<Recommendation[]> {
    const res = await client.get<Recommendation[]>('/equipment/recommendations', {params: {itemId}})
    return res.data
}

/**
 * Counts a collected list again against what the partners have free now, and says how many requests
 * it will turn into.
 */
export async function checkCollected(from: string, to: string, lines: CollectedLine[]): Promise<RecheckResponse> {
    const res = await client.post<RecheckResponse>('/equipment/collected', {from, to, lines})
    return res.data
}
