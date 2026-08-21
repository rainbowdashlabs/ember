/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** Where a piece of the cluster's gear currently is. */
export interface ClusterItem {
    id: number
    internalId: string
    name: string
    custody: string
    /** The station holding it, or null when it rests in the cluster's own store. */
    stationUid?: string | null
    stationName?: string | null
    /** The member wearing it, or null. */
    holderName?: string | null
}

/** A movement that has stopped on a step only the cluster can answer. */
export interface ClusterQueueEntry {
    movementId: number
    purpose: string
    stationUid?: string | null
    stationName?: string | null
    stepLabel?: string | null
    itemName?: string | null
    createdAt: string
}

export interface ClusterFlow {
    id: number
    name: string
    purpose: string
}

export async function listItems(): Promise<ClusterItem[]> {
    const res = await client.get<ClusterItem[]>('/cluster/inventory/items')
    return res.data
}

export async function listQueue(): Promise<ClusterQueueEntry[]> {
    const res = await client.get<ClusterQueueEntry[]>('/cluster/inventory/queue')
    return res.data
}

export async function listFlows(): Promise<ClusterFlow[]> {
    const res = await client.get<ClusterFlow[]>('/cluster/inventory/flows')
    return res.data
}

export async function createFlow(name: string, purpose: string): Promise<ClusterFlow> {
    const res = await client.post<ClusterFlow>('/cluster/inventory/flows', {name, purpose})
    return res.data
}

/**
 * Says whether the cluster keeps its gear here. With it off, its stations behave as if there were no
 * cluster above them where gear is concerned.
 */
export async function setUsesInventory(usesInventory: boolean): Promise<void> {
    await client.put('/cluster/inventory/settings', {usesInventory})
}
