/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import type {
    CapabilityRequest,
    components,
    FederatedKbBrowse,
    FederationCapability,
    FederationInfoResponse,
    FederationPartner,
    FederationShare,
    InviteCodeResponse,
    KbFile,
    KbShareRequest,
    KbShareResponse,
    PairRequestResponse,
    PartnerResponse,
    ProtocolShareRequest,
    QuizCatalog,
    QuizShareRequest,
    StationPickerResult,
    TestProtocol,
} from './generated/schema'

type Schemas = components['schemas']

export type FederationStatusName = Schemas['FederationStatus']

/** Where a partnership stands: offered, in force, or put on hold by either side. */
export const FederationStatus = {
    PENDING: 'PENDING',
    ACTIVE: 'ACTIVE',
    SUSPENDED: 'SUSPENDED',
} as const satisfies Record<FederationStatusName, FederationStatusName>

export type CapabilityTypeName = Schemas['CapabilityType']

/** What two partners may share with one another, each switched on per direction. */
export const CapabilityType = {
    KB_SHARE: 'KB_SHARE',
    QUIZ_SHARE: 'QUIZ_SHARE',
    PROTOCOL_SHARE: 'PROTOCOL_SHARE',
    INVENTORY_LEND: 'INVENTORY_LEND',
    EVENT_SHARE: 'EVENT_SHARE',
    BOARD_SHARE: 'BOARD_SHARE',
    NEWS_SHARE: 'NEWS_SHARE',
} as const satisfies Record<CapabilityTypeName, CapabilityTypeName>

export type CapabilityDirectionName = Schemas['Direction']

/** Whether a capability lets this station take from a partner or hand to it. */
export const CapabilityDirection = {
    IMPORT: 'IMPORT',
    EXPORT: 'EXPORT',
} as const satisfies Record<CapabilityDirectionName, CapabilityDirectionName>

/** A shared article or folder as the federated browse answers it. */
export type SharedContentItem = FederatedKbBrowse['files'][number] | FederatedKbBrowse['folders'][number]

export async function listPartners(): Promise<PartnerResponse[]> {
    const res = await client.get<PartnerResponse[]>('/federation/partners')
    return res.data
}

export async function createInvite(): Promise<InviteCodeResponse> {
    const res = await client.post<InviteCodeResponse>('/federation/invite')
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

export async function getCapabilities(partnerId: number): Promise<FederationCapability[]> {
    const res = await client.get<FederationCapability[]>(`/federation/partners/${partnerId}/capabilities`)
    return res.data
}

export async function setCapabilities(partnerId: number, capabilities: CapabilityRequest[]): Promise<FederationCapability[]> {
    const res = await client.put<FederationCapability[]>(`/federation/partners/${partnerId}/capabilities`, capabilities)
    return res.data
}

const kbShares = createCrudResource<KbShareResponse, KbShareRequest, KbShareRequest, KbShareResponse, FederationShare>(
    '/federation/shares/kb',
)
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

export async function browseSharedKb(): Promise<FederatedKbBrowse> {
    const res = await client.get<FederatedKbBrowse>('/federated/kb')
    return res.data
}

/** What is inside a folder a partner shares. */
export async function browseSharedKbFolder(stationUid: string, folderId: number): Promise<FederatedKbBrowse> {
    const res = await client.get<FederatedKbBrowse>(`/federated/${stationUid}/kb/folders/${folderId}`)
    return res.data
}

export async function copyKbFile(fileId: number): Promise<KbFile> {
    const res = await client.post<KbFile>(`/federated/kb/files/${fileId}/copy`)
    return res.data
}

export async function copyQuizCatalog(catalogId: number): Promise<QuizCatalog> {
    const res = await client.post<QuizCatalog>(`/federated/quiz/catalogs/${catalogId}/copy`)
    return res.data
}

export async function copyProtocol(protocolId: number): Promise<TestProtocol> {
    const res = await client.post<TestProtocol>(`/federated/protocols/${protocolId}/copy`)
    return res.data
}

export async function listPairRequests(): Promise<PairRequestResponse[]> {
    const res = await client.get<PairRequestResponse[]>('/federation/requests')
    return res.data
}

export async function acceptPairRequest(id: number): Promise<void> {
    await client.post(`/federation/requests/${id}/accept`)
}

export async function declinePairRequest(id: number): Promise<void> {
    await client.post(`/federation/requests/${id}/decline`)
}

export async function getFederationInfo(): Promise<FederationInfoResponse> {
    const res = await client.get<FederationInfoResponse>('/federation/info')
    return res.data
}

/** The partner stations the page editor's partner list may offer, gated by page editing. */
export async function searchFederationStations(query?: string, limit = 20): Promise<StationPickerResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<StationPickerResult[]>('/federation/stations/search', {params})
    return res.data
}
