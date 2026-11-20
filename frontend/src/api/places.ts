/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {GeocodeAnswer, PlaceAnswer, PlaceDraft, StationPlace} from './generated/schema'

/** The stored pin of a place text of the station, if it has one. */
export async function findPlace(text: string): Promise<StationPlace | null> {
    const res = await client.get<PlaceAnswer>('/station/places', {params: {text}})
    return res.data.place ?? null
}

/** Stores the pin of a place text; every appointment naming the place shows it. */
export async function savePlace(draft: PlaceDraft): Promise<StationPlace> {
    const res = await client.put<StationPlace>('/station/places', draft)
    return res.data
}

/** Takes the pin off a place text; every appointment naming the place shows it without a map again. */
export async function removePlace(text: string): Promise<void> {
    await client.delete('/station/places', {params: {text}})
}

/** Looks a place text up on the map near the station; nothing is stored. */
export async function geocodePlace(text: string): Promise<GeocodeAnswer> {
    const res = await client.post<GeocodeAnswer>('/station/places/geocode', {text})
    return res.data
}

/** The pins of an appointment's place fields on the given date, as far as they have one. */
export async function eventPlaces(eventId: number, date?: string | null): Promise<StationPlace[]> {
    const res = await client.get<StationPlace[]>(`/events/${eventId}/places`, {params: date ? {date} : {}})
    return res.data
}
