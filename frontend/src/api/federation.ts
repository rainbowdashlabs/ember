/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface FederationPartner {
    id: number
    stationId: string
    partnerStationId: string
    inviteCode: string | null
    publicKey: string | null
    partnerPublicKey: string | null
    status: 'PENDING' | 'ACTIVE' | 'SUSPENDED'
    federationVersion: string
    createdAt: string
    updatedAt: string
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

export interface SharedContentItem {
    remoteId: number
    title: string
    description: string
    stationName: string
    stationId: string
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

export async function listKbShares(): Promise<FederationShare[]> {
    const res = await client.get<FederationShare[]>('/federation/shares/kb')
    return res.data
}

export async function createKbShare(data: { fileId?: number; folderId?: number; shareScope?: string }): Promise<FederationShare> {
    const res = await client.post<FederationShare>('/federation/shares/kb', data)
    return res.data
}

export async function deleteKbShare(id: number): Promise<void> {
    await client.delete(`/federation/shares/kb/${id}`)
}

export async function listQuizShares(): Promise<FederationShare[]> {
    const res = await client.get<FederationShare[]>('/federation/shares/quiz')
    return res.data
}

export async function createQuizShare(data: { catalogId: number; shareScope?: string }): Promise<FederationShare> {
    const res = await client.post<FederationShare>('/federation/shares/quiz', data)
    return res.data
}

export async function deleteQuizShare(id: number): Promise<void> {
    await client.delete(`/federation/shares/quiz/${id}`)
}

export async function listProtocolShares(): Promise<FederationShare[]> {
    const res = await client.get<FederationShare[]>('/federation/shares/protocol')
    return res.data
}

export async function createProtocolShare(data: { protocolId: number; shareScope?: string }): Promise<FederationShare> {
    const res = await client.post<FederationShare>('/federation/shares/protocol', data)
    return res.data
}

export async function deleteProtocolShare(id: number): Promise<void> {
    await client.delete(`/federation/shares/protocol/${id}`)
}

// -- Browse shared content --

export async function browseSharedKb(): Promise<SharedContentItem[]> {
    const res = await client.get<SharedContentItem[]>('/federated/kb')
    return res.data
}

export async function browseSharedQuiz(): Promise<SharedContentItem[]> {
    const res = await client.get<SharedContentItem[]>('/federated/quiz/catalogs')
    return res.data
}

export async function browseSharedProtocols(): Promise<SharedContentItem[]> {
    const res = await client.get<SharedContentItem[]>('/federated/protocols')
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

export async function getFederationInfo(): Promise<{ federationVersion: string; supportedCapabilities: string[] }> {
    const res = await client.get<{ federationVersion: string; supportedCapabilities: string[] }>('/federation/info')
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
