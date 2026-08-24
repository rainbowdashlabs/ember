/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ProfileFieldConfig} from './profileFields'
import type {MemberIdentity, StationUserTypeName} from './types'
import {uploadFile} from './upload'
import {downloadAuthed} from '@/util/downloadAuthed'

// -- The cluster's own people --

export interface ClusterMemberSummary {
    id: number
    name?: string | null
    email?: string | null
    userType: string
}

export interface ClusterGroupSummary {
    id: number
    name: string
}

export interface ClusterMemberDetail {
    member: ClusterMemberSummary
    /** What they hold in their own right, which is the only part editable per member. */
    direct: string[]
    groups: ClusterGroupSummary[]
    /** Everything they hold once type, grants and groups are put together. */
    resolved: string[]
}

export interface ClusterGroupDetail {
    id: number
    name: string
    permissions: string[]
    memberIds: number[]
}

/**
 * Who runs this cluster.
 *
 * Readable by anybody who belongs to it, unlike the full member list: somebody who has been given one job
 * here should be able to see who to ask about the rest without being trusted with the roll.
 */
export async function listAdministrators(): Promise<ClusterMemberSummary[]> {
    const res = await client.get<ClusterMemberSummary[]>('/cluster/administrators')
    return res.data
}

export async function listMembers(): Promise<ClusterMemberSummary[]> {
    const res = await client.get<ClusterMemberSummary[]>('/cluster/members')
    return res.data
}

export async function addMember(email: string, userType: string): Promise<ClusterMemberSummary> {
    const res = await client.post<ClusterMemberSummary>('/cluster/members', {email, userType})
    return res.data
}

export async function getMember(memberId: number): Promise<ClusterMemberDetail> {
    const res = await client.get<ClusterMemberDetail>(`/cluster/members/${memberId}`)
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

export async function listGroups(): Promise<ClusterGroupSummary[]> {
    const res = await client.get<ClusterGroupSummary[]>('/cluster/member-groups')
    return res.data
}

export async function createGroup(name: string): Promise<ClusterGroupSummary> {
    const res = await client.post<ClusterGroupSummary>('/cluster/member-groups', {name})
    return res.data
}

export async function getGroup(groupId: number): Promise<ClusterGroupDetail> {
    const res = await client.get<ClusterGroupDetail>(`/cluster/member-groups/${groupId}`)
    return res.data
}

/** Every field is optional: renaming a group need not resend who is in it. */
export async function updateGroup(
    groupId: number,
    data: {name?: string; permissions?: string[]; memberIds?: number[]},
): Promise<void> {
    await client.put(`/cluster/member-groups/${groupId}`, data)
}

export async function deleteGroup(groupId: number): Promise<void> {
    await client.delete(`/cluster/member-groups/${groupId}`)
}

// -- The people at the cluster's stations --

export interface ManagedStation {
    uid: string
    name: string
}

export interface ManagedMember {
    id: number
    uid: string
    stationUid: string
    stationName: string
    name: string
    email: string
    userType: string
    joinDate?: string | null
    former: boolean
    /** A station's owner cannot be edited from the cluster. */
    stationOwner: boolean
    /** Who this is, as every list in Ember draws a person: the name, the colour, the tag and the avatar. */
    identity: MemberIdentity
    /**
     * Every station of the association this person belongs to, comma separated. The search returns one
     * row per membership, so somebody at two stations is two rows; naming all of them on each is what
     * stops the list saying they are two people.
     */
    stationNames: string
}

export interface ManagedMemberPage {
    members: ManagedMember[]
    /** How many the search found altogether, not how many are on this page. */
    total: number
    page: number
    size: number
}

export interface ManagedMemberQuery {
    q?: string
    stationUid?: string
    userType?: string
    includeFormer?: boolean
    page?: number
    size?: number
}

export async function searchManagedMembers(query: ManagedMemberQuery): Promise<ManagedMemberPage> {
    const res = await client.get<ManagedMemberPage>('/cluster/members/manage/search', {params: query})
    return res.data
}

export async function listManagedStations(): Promise<ManagedStation[]> {
    const res = await client.get<ManagedStation[]>('/cluster/members/manage/stations')
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
 * A question asked of somebody at one of the association's stations.
 *
 * <p>Carries which of the two tables it lives in, because the station's own questions and the
 * association's are shown together and an answer has to go back where its question came from.
 */
export interface ManagedProfileField {
    id: number
    name: string
    fieldType: string
    config: ProfileFieldConfig
    position: number
    scope: string
    origin: string
    /** Whether the people at the station may read the answer but not write it. */
    readonlyAtStation: boolean
}

export interface ManagedProfileValue {
    fieldId: number
    value: string
    origin: string
}

export interface ManagedMemberProfile {
    memberId: number
    name: string
    fields: ManagedProfileField[]
    values: ManagedProfileValue[]
}

export async function getManagedMemberProfile(memberId: number): Promise<ManagedMemberProfile> {
    const res = await client.get<ManagedMemberProfile>(`/cluster/members/manage/${memberId}/profile`)
    return res.data
}

export async function setManagedMemberProfile(
    memberId: number,
    values: ManagedProfileValue[],
): Promise<void> {
    await client.put(`/cluster/members/manage/${memberId}/profile`, {values})
}

/** Somebody being taken on at a station of the association. */
export interface NewManagedMember {
    firstName: string
    lastName: string
    /** Leave it out for somebody who is not meant to sign in. */
    email?: string
    userType?: StationUserTypeName
}

export interface NewManagedMemberResponse {
    memberId: number
    accountId: number
    email: string
}

/**
 * Takes somebody on at one of the association's stations.
 *
 * <p>The station is named first because a member belongs to a station and the association is standing in
 * for one. Its identity is in the path for that reason rather than travelling in the session.
 */
export async function createManagedMember(
    stationUid: string,
    member: NewManagedMember,
): Promise<NewManagedMemberResponse> {
    const res = await client.post<NewManagedMemberResponse>(
        `/cluster/members/manage/stations/${stationUid}/members`, member)
    return res.data
}

/** One document filed about somebody at a station of the association. */
export interface ManagedMemberDocument {
    id: number
    title: string
    fileName: string
    mimeType: string
    sizeBytes: number
    createdAt: string
}

export async function listManagedMemberDocuments(memberId: number): Promise<ManagedMemberDocument[]> {
    const res = await client.get<ManagedMemberDocument[]>(`/cluster/members/manage/${memberId}/documents`)
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
): Promise<ManagedMemberDocument> {
    return uploadFile<ManagedMemberDocument>(
        `/cluster/members/manage/${memberId}/documents`, {file, title})
}

/** Fetches the bytes of one with the session and hands them to the reader under their own name. */
export async function downloadManagedMemberDocument(
    documentId: number,
    fileName: string,
): Promise<void> {
    await downloadAuthed(`/cluster/members/manage/documents/${documentId}/content`, fileName)
}
