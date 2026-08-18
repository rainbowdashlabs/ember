/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'

export interface FederationContract {
    core: string
    features: Record<string, string>
}

export interface FederationPartner {
    id: number
    stationId: string
    partnerStationId: string
    inviteCode: string | null
    publicKey: string | null
    partnerPublicKey: string | null
    status: 'PENDING' | 'ACTIVE' | 'SUSPENDED'
    federationContract: FederationContract | null
    createdAt: string
    updatedAt: string
    remoteHost: string | null
}

export interface PartnerResponse {
    partner: FederationPartner
    partnerStationName: string
}

export interface FederationCapability {
    id: number
    partnerId: number
    capability: string
    direction: string
    enabled: boolean
}

export interface FederationShare {
    id: number
    stationId: string
    fileId: number | null
    folderId: number | null
    catalogId: number | null
    protocolId: number | null
    shareScope: string
}

/**
 * An item a partner shares, in the shape every federated browse endpoint answers. The station
 * UUID is what addresses the item on the federated read routes; it is null when the partnership
 * behind the item no longer resolves, and such an item can only be copied, not opened.
 */
export interface SharedContentItem {
    remoteId: number
    title: string
    description: string
    stationName: string
    stationUid: string | null
    partnerId: number
}

// -- Partners --

export async function listPartners(): Promise<PartnerResponse[]> {
    const res = await client.get<PartnerResponse[]>('/federation/partners')
    return res.data
}

export async function createInvite(): Promise<{ inviteCode: string }> {
    const res = await client.post<{ inviteCode: string }>('/federation/invite')
    return res.data
}

export async function acceptInvite(inviteCode: string): Promise<FederationPartner> {
    const res = await client.post<FederationPartner>('/federation/accept', { inviteCode })
    return res.data
}

export async function getPartner(id: number): Promise<PartnerResponse> {
    const res = await client.get<PartnerResponse>(`/federation/partners/${id}`)
    return res.data
}

export async function suspendPartner(id: number): Promise<FederationPartner> {
    const res = await client.post<FederationPartner>(`/federation/partners/${id}/suspend`)
    return res.data
}

export async function resumePartner(id: number): Promise<FederationPartner> {
    const res = await client.post<FederationPartner>(`/federation/partners/${id}/resume`)
    return res.data
}

export async function endFederation(id: number): Promise<void> {
    await client.delete(`/federation/partners/${id}`)
}

// -- Capabilities --

export async function getCapabilities(partnerId: number): Promise<FederationCapability[]> {
    const res = await client.get<FederationCapability[]>(`/federation/partners/${partnerId}/capabilities`)
    return res.data
}

export async function setCapabilities(partnerId: number, capabilities: { capability: string; direction: string; enabled: boolean }[]): Promise<FederationCapability[]> {
    const res = await client.put<FederationCapability[]>(`/federation/partners/${partnerId}/capabilities`, capabilities)
    return res.data
}

// -- Shares --

interface KbShareRequest {
    fileId?: number
    folderId?: number
    shareScope?: string
}

interface QuizShareRequest {
    catalogId: number
    shareScope?: string
}

interface ProtocolShareRequest {
    protocolId: number
    shareScope?: string
}

const kbShares = createCrudResource<FederationShare, KbShareRequest>('/federation/shares/kb')
const quizShares = createCrudResource<FederationShare, QuizShareRequest>('/federation/shares/quiz')
const protocolShares = createCrudResource<FederationShare, ProtocolShareRequest>('/federation/shares/protocol')

export const listKbShares = kbShares.list
export const createKbShare = kbShares.create
export const deleteKbShare = kbShares.remove

export const listQuizShares = quizShares.list
export const createQuizShare = quizShares.create
export const deleteQuizShare = quizShares.remove

export const listProtocolShares = protocolShares.list
export const createProtocolShare = protocolShares.create
export const deleteProtocolShare = protocolShares.remove

// -- Browse shared content --

export async function browseSharedKb(): Promise<SharedContentItem[]> {
    const res = await client.get<SharedContentItem[]>('/federated/kb')
    return res.data
}

// -- Copy --

export async function copyKbFile(fileId: number): Promise<unknown> {
    const res = await client.post(`/federated/kb/files/${fileId}/copy`)
    return res.data
}

export async function copyQuizCatalog(catalogId: number): Promise<unknown> {
    const res = await client.post(`/federated/quiz/catalogs/${catalogId}/copy`)
    return res.data
}

export async function copyProtocol(protocolId: number): Promise<unknown> {
    const res = await client.post(`/federated/protocols/${protocolId}/copy`)
    return res.data
}

// -- Pair Requests --

export interface PairRequest {
    id: number
    stationName: string
    createdAt: string
}

export async function listPairRequests(): Promise<PairRequest[]> {
    const res = await client.get<PairRequest[]>('/federation/requests')
    return res.data
}

export async function acceptPairRequest(id: number): Promise<void> {
    await client.post(`/federation/requests/${id}/accept`)
}

export async function declinePairRequest(id: number): Promise<void> {
    await client.post(`/federation/requests/${id}/decline`)
}

// -- Info --

export async function getFederationInfo(): Promise<{ contract: FederationContract }> {
    const res = await client.get<{ contract: FederationContract }>('/federation/info')
    return res.data
}

// -- Page-editor PARTNER_STATIONS picker. PAGE_EDIT-gated. --

export interface StationPickerResult {
    stationUid: string
    name: string
    city: string | null
    country: string | null
    logoUrl: string | null
    selectable: boolean
}

export async function searchFederationStations(query?: string, limit = 20): Promise<StationPickerResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<StationPickerResult[]>('/federation/stations/search', {params})
    return res.data
}
