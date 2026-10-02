/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {LossReportRequirementName} from './inventory'
import type {MovementPurposeName} from './movements'
import type {
    ClusterFlowResponse,
    ClusterInventoryTag,
    ClusterItemResponse,
    ClusterQueueResponse,
    ClusterStepRequest,
    ClusterStepResponse,
    ClusterTagRequest,
    InventoryStatResponse,
    LossReportSettings,
    SendableItem,
} from './generated/schema'

export async function statistics(): Promise<InventoryStatResponse[]> {
    const res = await client.get<InventoryStatResponse[]>('/cluster/inventory/statistics')
    return res.data
}

export async function listItems(): Promise<ClusterItemResponse[]> {
    const res = await client.get<ClusterItemResponse[]>('/cluster/inventory/items')
    return res.data
}

export async function listQueue(): Promise<ClusterQueueResponse[]> {
    const res = await client.get<ClusterQueueResponse[]>('/cluster/inventory/queue')
    return res.data
}

export async function listFlows(): Promise<ClusterFlowResponse[]> {
    const res = await client.get<ClusterFlowResponse[]>('/cluster/inventory/flows')
    return res.data
}

export async function createFlow(name: string, purpose: MovementPurposeName): Promise<ClusterFlowResponse> {
    const res = await client.post<ClusterFlowResponse>('/cluster/inventory/flows', {name, purpose})
    return res.data
}

export async function renameFlow(flowId: number, name: string): Promise<void> {
    await client.put(`/cluster/inventory/flows/${flowId}`, {name})
}

/** Retires a chain, which keeps it readable for the movements that walked it. */
export async function archiveFlow(flowId: number): Promise<void> {
    await client.delete(`/cluster/inventory/flows/${flowId}`)
}

export async function addStep(flowId: number, step: ClusterStepRequest): Promise<ClusterStepResponse> {
    const res = await client.post<ClusterStepResponse>(`/cluster/inventory/flows/${flowId}/steps`, step)
    return res.data
}

export async function updateStep(stepId: number, step: ClusterStepRequest): Promise<void> {
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

export async function getLossReportSettings(): Promise<LossReportSettings> {
    const res = await client.get<LossReportSettings>('/cluster/inventory/loss-report')
    return res.data
}

export async function setLossReportSettings(requires: LossReportRequirementName): Promise<void> {
    await client.put('/cluster/inventory/loss-report', {requires})
}

export async function listTags(): Promise<ClusterInventoryTag[]> {
    const res = await client.get<ClusterInventoryTag[]>('/cluster/inventory-tags')
    return res.data
}

export async function createTag(body: ClusterTagRequest): Promise<ClusterInventoryTag> {
    const res = await client.post<ClusterInventoryTag>('/cluster/inventory-tags', body)
    return res.data
}

export async function updateTag(tagId: number, body: ClusterTagRequest): Promise<ClusterInventoryTag> {
    const res = await client.put<ClusterInventoryTag>(`/cluster/inventory-tags/${tagId}`, body)
    return res.data
}

export async function deleteTag(tagId: number): Promise<void> {
    await client.delete(`/cluster/inventory-tags/${tagId}`)
}
