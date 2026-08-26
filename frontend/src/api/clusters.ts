/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** What a cluster member carries by default. */
export const ClusterUserType = {
    CLUSTER_USER: 'CLUSTER_USER',
    CLUSTER_ADMIN: 'CLUSTER_ADMIN',
} as const

export type ClusterUserTypeName = (typeof ClusterUserType)[keyof typeof ClusterUserType]

/**
 * What a cluster member may do. The mirror of the station's permissions, and separate from them: a cluster
 * has its own members and its own grants.
 */
export const ClusterPermission = {
    USER: 'USER',
    LOGIN: 'LOGIN',
    CLUSTER_GENERAL: 'CLUSTER_GENERAL',
    CLUSTER_LOOK_AND_FEEL: 'CLUSTER_LOOK_AND_FEEL',
    CLUSTER_FEDERATION: 'CLUSTER_FEDERATION',
    CLUSTER_MODULES: 'CLUSTER_MODULES',
    CLUSTER_STORAGE: 'CLUSTER_STORAGE',
    CLUSTER_STATIONS: 'CLUSTER_STATIONS',
    CLUSTER_MEMBER_READ: 'CLUSTER_MEMBER_READ',
    CLUSTER_MEMBER_EDIT: 'CLUSTER_MEMBER_EDIT',
    CLUSTER_MEMBER_FIELDS: 'CLUSTER_MEMBER_FIELDS',
    CLUSTER_MEMBER_EXPORT: 'CLUSTER_MEMBER_EXPORT',
    CLUSTER_MEMBER_MANAGER: 'CLUSTER_MEMBER_MANAGER',
    CLUSTER_INVENTORY_READ: 'CLUSTER_INVENTORY_READ',
    CLUSTER_INVENTORY_EDIT: 'CLUSTER_INVENTORY_EDIT',
    CLUSTER_INVENTORY_TRANSFER: 'CLUSTER_INVENTORY_TRANSFER',
    CLUSTER_INVENTORY_EXCHANGE: 'CLUSTER_INVENTORY_EXCHANGE',
    CLUSTER_INVENTORY_MANAGER: 'CLUSTER_INVENTORY_MANAGER',
    CLUSTER_FIELD_EDIT: 'CLUSTER_FIELD_EDIT',
    CLUSTER_FIELD_MANAGER: 'CLUSTER_FIELD_MANAGER',
    CLUSTER_KNOWLEDGE_EDIT: 'CLUSTER_KNOWLEDGE_EDIT',
    CLUSTER_KNOWLEDGE_MANAGER: 'CLUSTER_KNOWLEDGE_MANAGER',
    CLUSTER_NEWS_EDIT: 'CLUSTER_NEWS_EDIT',
    CLUSTER_NEWS_MANAGER: 'CLUSTER_NEWS_MANAGER',
    CLUSTER_EVENT_EDIT: 'CLUSTER_EVENT_EDIT',
    CLUSTER_EVENT_MANAGER: 'CLUSTER_EVENT_MANAGER',
    CLUSTER_MANAGER: 'CLUSTER_MANAGER',
    CLUSTER_ADMINISTRATOR: 'CLUSTER_ADMINISTRATOR',
} as const

export type ClusterPermissionName = (typeof ClusterPermission)[keyof typeof ClusterPermission]

export interface Cluster {
    uid: string
    name: string
    description?: string | null
    /** The station shell the cluster owns, as its station identity. */
    homeStationId: string
    autoFederate: boolean
    themeLocked: boolean
    colorsLocked: boolean
    feelLocked: boolean
    logoLocked: boolean
    storagePoolBytes?: number | null
    /** Whether the cluster keeps its gear here, which is what lets its own steps appear in a movement. */
    usesInventory?: boolean
}

export interface ClusterRequest {
    name: string
    description?: string | null
    /** Leave undefined to keep the setting as it is. */
    autoFederate?: boolean
}

/** The clusters the signed-in account may act for. */
export async function listMine(): Promise<Cluster[]> {
    const res = await client.get<Cluster[]>('/clusters')
    return res.data
}

/** Every cluster on the instance, for an administrator. */
export async function listAll(): Promise<Cluster[]> {
    const res = await client.get<Cluster[]>('/clusters/all')
    return res.data
}

/** The cluster the request is acting for, named by the header. */
export async function getActive(): Promise<Cluster> {
    const res = await client.get<Cluster>('/cluster')
    return res.data
}

export async function createCluster(data: ClusterRequest): Promise<Cluster> {
    const res = await client.post<Cluster>('/clusters', data)
    return res.data
}

export async function updateActive(data: ClusterRequest): Promise<Cluster> {
    const res = await client.put<Cluster>('/cluster', data)
    return res.data
}

export async function deleteCluster(uid: string): Promise<void> {
    await client.delete(`/clusters/${uid}`)
}

/**
 * Makes an account the first person who may act for a cluster.
 *
 * The one cluster call an instance administrator makes from outside it. A cluster the instance has just
 * created has nobody in it and every other cluster call asks for a right only a member can hold, so without
 * this a new cluster would never get its first person.
 */
export async function appointAdministrator(uid: string, accountUid: string): Promise<void> {
    await client.post(`/clusters/${uid}/administrators`, {accountUid})
}
