/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {LossReportRequirementName} from './inventory'
import type {MovementFlowStep, MovementPurposeName, StepRequest} from './movements'

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
    /** The size it is cut to, absent where the inventory it belongs to keeps no sizes. */
    sizeId?: number | null
    sizeLabel?: string | null
}

/** How much the association owns of one kind of thing, and where those pieces stand. */
export interface ClusterInventoryStat {
    inventoryId: number
    inventoryName: string
    total: number
    /** Resting in the association's own store. */
    inStore: number
    /** At one of its stations, on the way there included. */
    atStation: number
    withMember: number
    lent: number
    lost: number
    sizes: ClusterSizeStat[]
}

/** The same counts for one size of one kind of thing. */
export interface ClusterSizeStat {
    sizeId: number
    label: string
    total: number
    inStore: number
    atStation: number
    withMember: number
    lent: number
    lost: number
}

export async function statistics(): Promise<ClusterInventoryStat[]> {
    const res = await client.get<ClusterInventoryStat[]>('/cluster/inventory/statistics')
    return res.data
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

/**
 * A chain the association's gear walks, with the steps it is made of.
 *
 * <p>The steps travel with it because a chain is its steps. The same shape the station's screens speak,
 * so the station's flow card draws it: {@code ownedByCluster} means "somebody above me owns this", and
 * at the association nobody is.
 */
export interface ClusterFlow {
    id: number
    name: string
    purpose: MovementPurposeName
    archived: boolean
    steps: MovementFlowStep[]
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

export async function renameFlow(flowId: number, name: string): Promise<void> {
    await client.put(`/cluster/inventory/flows/${flowId}`, {name})
}

/** Retires a chain, which keeps it readable for the movements that walked it. */
export async function archiveFlow(flowId: number): Promise<void> {
    await client.delete(`/cluster/inventory/flows/${flowId}`)
}

export async function addStep(flowId: number, step: StepRequest): Promise<MovementFlowStep> {
    const res = await client.post<MovementFlowStep>(`/cluster/inventory/flows/${flowId}/steps`, step)
    return res.data
}

export async function updateStep(stepId: number, step: StepRequest): Promise<void> {
    await client.put(`/cluster/inventory/flow-steps/${stepId}`, step)
}

export async function archiveStep(stepId: number): Promise<void> {
    await client.delete(`/cluster/inventory/flow-steps/${stepId}`)
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
