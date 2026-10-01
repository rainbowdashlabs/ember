/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    ClusterGroupDetailResponse,
    ClusterGroupResponse,
    ClusterGroupUpdateRequest,
    ClusterMemberDetailResponse,
    ClusterMemberResponse,
    ManagedStationResponse,
    MemberDocumentSummary,
    MemberPageResponse,
    MemberProfileResponse,
    MemberProfileValueRequest,
    NewMemberRequest,
    NewMemberResponse,
} from './generated/schema'
import type {PersonIdentity} from '@/util/personIdentity'
import {uploadFile} from './upload'
import {downloadAuthed} from '@/util/downloadAuthed'

/**
 * How a cluster member is drawn in the lists that draw a person: by their account, because somebody
 * who runs an association need belong to no station and so is nobody's member.
 */
export function clusterMemberIdentity(member: ClusterMemberResponse): PersonIdentity {
    return {accountUid: member.accountUid ?? undefined, name: member.name ?? member.email ?? ''}
}

/**
 * Who runs this cluster.
 *
 * Readable by anybody who belongs to it, unlike the full member list: somebody who has been given one job
 * here should be able to see who to ask about the rest without being trusted with the roll.
 */
export async function listAdministrators(): Promise<ClusterMemberResponse[]> {
    const res = await client.get<ClusterMemberResponse[]>('/cluster/administrators')
    return res.data
}

export async function listMembers(): Promise<ClusterMemberResponse[]> {
    const res = await client.get<ClusterMemberResponse[]>('/cluster/members')
    return res.data
}

/**
 * Gives somebody a job at the association, which is what makes them a member of it.
 *
 * <p>A first and last name are needed only for an address Ember has never seen; the server refuses
 * with {@link ACCOUNT_NAME_REQUIRED} until it has them, and the dialog asks then rather than up front.
 */
export async function addMember(
    email: string,
    userType: string,
    name?: {firstName: string; lastName: string},
): Promise<ClusterMemberResponse> {
    const res = await client.post<ClusterMemberResponse>('/cluster/members', {email, userType, ...name})
    return res.data
}

/** The refusal that means "no account yet, send a name and I will make one". */
export const ACCOUNT_NAME_REQUIRED = 'AccountNameRequiredException'

export async function getMember(memberId: number): Promise<ClusterMemberDetailResponse> {
    const res = await client.get<ClusterMemberDetailResponse>(`/cluster/members/${memberId}`)
    return res.data
}

export async function removeMember(memberId: number): Promise<void> {
    await client.delete(`/cluster/members/${memberId}`)
}

export async function setMemberUserType(memberId: number, userType: string): Promise<void> {
    await client.put(`/cluster/members/${memberId}/user-type`, {userType})
}

export async function setMemberPermissions(memberId: number, permissions: string[]): Promise<void> {
    await client.put(`/cluster/members/${memberId}/permissions`, {permissions})
}

/** The same membership a group's own screen writes, read and written from the member's end. */
export async function setMemberGroups(memberId: number, groupIds: number[]): Promise<void> {
    await client.put(`/cluster/members/${memberId}/groups`, {groupIds})
}

export async function listGroups(): Promise<ClusterGroupResponse[]> {
    const res = await client.get<ClusterGroupResponse[]>('/cluster/member-groups')
    return res.data
}

export async function createGroup(name: string): Promise<ClusterGroupResponse> {
    const res = await client.post<ClusterGroupResponse>('/cluster/member-groups', {name})
    return res.data
}

export async function getGroup(groupId: number): Promise<ClusterGroupDetailResponse> {
    const res = await client.get<ClusterGroupDetailResponse>(`/cluster/member-groups/${groupId}`)
    return res.data
}

/** Every field is optional: renaming a group need not resend who is in it. */
export async function updateGroup(groupId: number, data: ClusterGroupUpdateRequest): Promise<void> {
    await client.put(`/cluster/member-groups/${groupId}`, data)
}

export async function deleteGroup(groupId: number): Promise<void> {
    await client.delete(`/cluster/member-groups/${groupId}`)
}

export interface ManagedMemberQuery {
    q?: string
    stationUid?: string
    userType?: string
    includeFormer?: boolean
    page?: number
    size?: number
}

export async function searchManagedMembers(query: ManagedMemberQuery): Promise<MemberPageResponse> {
    const res = await client.get<MemberPageResponse>('/cluster/members/manage/search', {params: query})
    return res.data
}

export async function listManagedStations(): Promise<ManagedStationResponse[]> {
    const res = await client.get<ManagedStationResponse[]>('/cluster/members/manage/stations')
    return res.data
}

export async function setManagedUserType(memberId: number, userType: string): Promise<void> {
    await client.put(`/cluster/members/manage/${memberId}/user-type`, {userType})
}

export async function setManagedPermissions(memberId: number, permissions: string[]): Promise<void> {
    await client.put(`/cluster/members/manage/${memberId}/permissions`, {permissions})
}

export async function archiveManagedMember(memberId: number): Promise<void> {
    await client.delete(`/cluster/members/manage/${memberId}`)
}

/**
 * What is asked of somebody at one of the association's stations, with their answers.
 *
 * <p>Each question carries which of the two tables it lives in, because the station's own questions and
 * the association's are shown together and an answer has to go back where its question came from.
 */
export async function getManagedMemberProfile(memberId: number): Promise<MemberProfileResponse> {
    const res = await client.get<MemberProfileResponse>(`/cluster/members/manage/${memberId}/profile`)
    return res.data
}

export async function setManagedMemberProfile(
    memberId: number,
    values: MemberProfileValueRequest[],
): Promise<void> {
    await client.put(`/cluster/members/manage/${memberId}/profile`, {values})
}

/**
 * Takes somebody on at one of the association's stations.
 *
 * <p>The station is named first because a member belongs to a station and the association is standing in
 * for one. Its identity is in the path for that reason rather than travelling in the session.
 */
export async function createManagedMember(
    stationUid: string,
    member: NewMemberRequest,
): Promise<NewMemberResponse> {
    const res = await client.post<NewMemberResponse>(
        `/cluster/members/manage/stations/${stationUid}/members`, member)
    return res.data
}

export async function listManagedMemberDocuments(memberId: number): Promise<MemberDocumentSummary[]> {
    const res = await client.get<MemberDocumentSummary[]>(`/cluster/members/manage/${memberId}/documents`)
    return res.data
}

/**
 * Files a document about somebody at a station of the association.
 *
 * <p>It belongs to the station that holds them, which is where the person is and where it stays when
 * the station leaves.
 */
export async function uploadManagedMemberDocument(
    memberId: number,
    file: File,
    title: string,
): Promise<MemberDocumentSummary> {
    return uploadFile<MemberDocumentSummary>(
        `/cluster/members/manage/${memberId}/documents`, {file, title})
}

/** Fetches the bytes of one with the session and hands them to the reader under their own name. */
export async function downloadManagedMemberDocument(
    documentId: number,
    fileName: string,
): Promise<void> {
    await downloadAuthed(`/cluster/members/manage/documents/${documentId}/content`, fileName)
}
