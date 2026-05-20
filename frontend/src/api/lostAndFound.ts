/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ClaimLostAndFoundRequest, CreateLostAndFoundRequest, LostAndFoundItem, MessageResponse} from './types'

export async function listItems(): Promise<LostAndFoundItem[]> {
    const res = await client.get<LostAndFoundItem[]>('/lost-and-found')
    return res.data
}

export async function getItem(id: number): Promise<LostAndFoundItem> {
    const res = await client.get<LostAndFoundItem>(`/lost-and-found/${id}`)
    return res.data
}

export async function createItem(data: CreateLostAndFoundRequest): Promise<LostAndFoundItem> {
    const res = await client.post<LostAndFoundItem>('/lost-and-found', data)
    return res.data
}

export async function uploadImage(id: number, file: File): Promise<void> {
    const formData = new FormData()
    formData.append('image', file)
    await client.post(`/lost-and-found/${id}/image`, formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    })
}

export function imageUrl(id: number, size?: number): string {
    const base = `${client.defaults.baseURL}/lost-and-found/${id}/image`
    return size ? `${base}?size=${size}` : base
}

export async function claimItem(id: number, request?: ClaimLostAndFoundRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>(`/lost-and-found/${id}/claim`, request ?? {})
    return res.data
}

export async function markProvided(id: number): Promise<void> {
    await client.post(`/lost-and-found/${id}/provided`)
}

export async function deleteItem(id: number): Promise<void> {
    await client.delete(`/lost-and-found/${id}`)
}
