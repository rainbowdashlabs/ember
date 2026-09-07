/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import {uploadFile} from './upload'
import type {MessageResponse} from './types'
import {prepareImageUpload} from '@/util/imageUpload'

export interface LostAndFoundItem {
    id: number
    stationId: string
    description?: string
    foundAt?: string
    hasImage: boolean
    claimedBy?: number | null
    claimedByName?: string | null
    claimedAt?: string | null
    createdBy: number
    createdAt: string
}

export interface CreateLostAndFoundRequest {
    description?: string
    foundAt?: string
}

export interface ClaimLostAndFoundRequest {
    memberId?: number | null
}

const items = createCrudResource<LostAndFoundItem, CreateLostAndFoundRequest>('/lost-and-found')

export const listItems = items.list
export const getItem = items.get
export const createItem = items.create
export const deleteItem = items.remove

/**
 * Sends the picture of a found item, redrawn to a format and a size the endpoint takes. A photo
 * straight off a phone is neither, so the browser does the shrinking before anything is uploaded.
 */
export async function uploadImage(id: number, file: File): Promise<void> {
    await uploadFile(`/lost-and-found/${id}/image`, {image: await prepareImageUpload(file)})
}

export function imageUrl(id: number, size?: number): string {
    const base = `${client.defaults.baseURL}/lost-and-found/${id}/image`
    return size ? `${base}?size=${size}` : base
}

export async function claimItem(id: number, request?: ClaimLostAndFoundRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>(`/lost-and-found/${id}/claim`, request ?? {})
    return res.data
}

export async function releaseItem(id: number): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>(`/lost-and-found/${id}/release`)
    return res.data
}

export async function markProvided(id: number): Promise<void> {
    await client.post(`/lost-and-found/${id}/provided`)
}
