/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {PictureResponse} from './generated/schema'
import {prepareImageUpload} from '@/util/imageUpload'

/**
 * The address of a photo with its key in it. Every photo is kept under a key that changes with each
 * upload, so a browser never shows a cached older photo after a new one was sent.
 */
function withKey(path: string, pictureKey: string, size?: number): string {
    const params = new URLSearchParams({v: pictureKey})
    if (size) params.set('size', String(size))
    return `${client.defaults.baseURL}${path}?${params.toString()}`
}

/** Sends a new photo for an inventory, shrunk in the browser first, and answers with its key. */
export async function uploadInventoryPicture(inventoryId: number, file: File): Promise<string> {
    const answer = await uploadFile<PictureResponse>(
        `/inventories/${inventoryId}/picture`, {image: await prepareImageUpload(file)}, 'put')
    return answer.pictureKey
}

export async function removeInventoryPicture(inventoryId: number): Promise<void> {
    await client.delete(`/inventories/${inventoryId}/picture`)
}

/** Sends a new photo for a kind, shown on its pieces before the inventory's, and answers with its key. */
export async function uploadArtPicture(inventoryId: number, artId: number, file: File): Promise<string> {
    const answer = await uploadFile<PictureResponse>(
        `/inventories/${inventoryId}/arts/${artId}/picture`, {image: await prepareImageUpload(file)}, 'put')
    return answer.pictureKey
}

export async function removeArtPicture(inventoryId: number, artId: number): Promise<void> {
    await client.delete(`/inventories/${inventoryId}/arts/${artId}/picture`)
}

export function inventoryPictureUrl(inventoryId: number, pictureKey: string, size?: number): string {
    return withKey(`/inventories/${inventoryId}/picture`, pictureKey, size)
}

export function artPictureUrl(inventoryId: number, artId: number, pictureKey: string, size?: number): string {
    return withKey(`/inventories/${inventoryId}/arts/${artId}/picture`, pictureKey, size)
}

/**
 * The photo a piece is shown with. The key is the one the page resolved itself, the kind's before the
 * inventory's, which is what keeps the address changing whenever that photo does.
 */
export function itemPictureUrl(itemId: number, pictureKey: string, size?: number): string {
    return withKey(`/inventory-items/${itemId}/picture`, pictureKey, size)
}

/** A photo of one of this station's inventories or kinds by its key alone, for lists that carry only the key. */
export function pictureUrl(pictureKey: string, size?: number): string {
    const base = `${client.defaults.baseURL}/inventory-pictures/${encodeURIComponent(pictureKey)}`
    return size ? `${base}?size=${size}` : base
}
