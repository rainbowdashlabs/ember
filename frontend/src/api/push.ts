/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {PushDevice, PushKey, SubscribeRequest} from './generated/schema'

/** The key a browser subscribes with, and whether this instance sends push messages at all. */
export async function getPushKey(): Promise<PushKey> {
    return (await client.get<PushKey>('/push/key')).data
}

/** The account's devices that receive push messages, the asking one marked as current. */
export async function listPushDevices(): Promise<PushDevice[]> {
    return (await client.get<PushDevice[]>('/push/subscriptions')).data
}

/**
 * Registers this browser for push messages, or refreshes it. A renewal, which the page sends on its own, answers
 * nothing for a browser the account removed by hand, since only allowing it again brings it back.
 */
export async function registerPushDevice(request: SubscribeRequest): Promise<PushDevice | null> {
    const response = await client.put<PushDevice>('/push/subscriptions', request)
    return response.status === 204 ? null : response.data
}

/** Forgets one of the account's devices. */
export async function removePushDevice(id: number): Promise<void> {
    await client.delete(`/push/subscriptions/${id}`)
}

/** Sends a test push to one of the account's devices. */
export async function testPushDevice(id: number): Promise<void> {
    await client.post(`/push/subscriptions/${id}/test`)
}
