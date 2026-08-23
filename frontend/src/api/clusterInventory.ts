/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {LossReportRequirementName} from './inventory'

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

/** One piece resting in the cluster's store, offered on the dispatch screen. */
export interface SendableItem {
    id: number
    internalId?: string | null
    name?: string | null
    inventoryId: number
    inventoryName: string
}

/**
 * The gear resting in the cluster's own store, which is what there is to send. Anything already out at a
 * station, on its way somewhere or missing is not in the store, however much the cluster owns it.
 */
export async function listSendable(): Promise<SendableItem[]> {
    const res = await client.get<SendableItem[]>('/cluster/inventory/dispatch')
    return res.data
}

/**
 * Sends a batch of the cluster's gear to one of its stations.
 *
 * <p>One movement carries the lot, so the station confirms one arrival rather than twenty.
 */
export async function dispatch(stationUid: string, itemIds: number[], reason: string): Promise<void> {
    await client.post('/cluster/inventory/dispatch', {stationUid, itemIds, reason})
}

/** What the cluster wants to read before it considers replacing something that was lost. */
export interface LossReportSettings {
    requires: LossReportRequirementName
}

export async function getLossReportSettings(): Promise<LossReportSettings> {
    const res = await client.get<LossReportSettings>('/cluster/inventory/loss-report')
    return res.data
}

export async function setLossReportSettings(requires: LossReportRequirementName): Promise<void> {
    await client.put('/cluster/inventory/loss-report', {requires})
}
