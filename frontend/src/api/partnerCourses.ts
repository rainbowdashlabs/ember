/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CourseDetail, CourseSignUp} from './generated/schema'

function base(stationUid: string, courseId: number): string {
    return `/federated/${stationUid}/events/courses/${courseId}`
}

/** A course a partner station shares, with the standing of the reader and everybody they look after. */
export async function getCourse(stationUid: string, courseId: number): Promise<CourseDetail> {
    return (await client.get<CourseDetail>(base(stationUid, courseId))).data
}

/** Signs the reader, or somebody they look after, up for a partner's course. */
export async function signUp(stationUid: string, courseId: number, parts: number[], memberId?: number): Promise<CourseSignUp> {
    return (await client.post<CourseSignUp>(`${base(stationUid, courseId)}/sign-up`, {parts, memberId})).data
}

/** Changes the parts chosen with a sign-up for a partner's course. */
export async function changeParts(stationUid: string, courseId: number, parts: number[], memberId?: number): Promise<CourseSignUp> {
    return (await client.put<CourseSignUp>(`${base(stationUid, courseId)}/sign-up`, {parts, memberId})).data
}

/** Withdraws a sign-up for a partner's course. */
export async function withdraw(stationUid: string, courseId: number, memberId?: number): Promise<void> {
    await client.delete(`${base(stationUid, courseId)}/sign-up`, {params: {memberId}})
}
