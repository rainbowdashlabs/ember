/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    CourseAwaiting,
    CourseCreateRequest,
    CourseDetail,
    CoursePartCreateRequest,
    CoursePartSummary,
    CourseSignUp,
    CourseSummary,
    EventCourseDetails,
    EventCoursePart,
    EventCourseSetDraft,
    RegistrationStatus,
    RestrictionAudience,
} from './generated/schema'

const COURSES = '/events/courses'

/** The courses meant for the reader, or every course for whoever runs events. */
export async function listCourses(): Promise<CourseSummary[]> {
    return (await client.get<CourseSummary[]>(COURSES)).data
}

/** A course with its sets, parts and the sign-ups of the reader's household. */
export async function getCourse(courseId: number): Promise<CourseDetail> {
    return (await client.get<CourseDetail>(`${COURSES}/${courseId}`)).data
}

/** Creates a course with its sets and audiences. */
export async function createCourse(request: CourseCreateRequest): Promise<CourseDetail> {
    return (await client.post<CourseDetail>(COURSES, request)).data
}

/**
 * Saves what the course editor holds in one go: details, and where given its sets and audiences.
 * Either all of it is kept or, where any part is refused, none of it.
 */
export async function updateCourse(
    courseId: number,
    details: EventCourseDetails,
    sets?: EventCourseSetDraft[],
    audiences?: {registration: RestrictionAudience; view: RestrictionAudience},
): Promise<CourseDetail> {
    return (await client.put<CourseDetail>(`${COURSES}/${courseId}`, {details, sets, ...audiences})).data
}

/** Deletes a course; its parts become appointments of their own. */
export async function deleteCourse(courseId: number): Promise<void> {
    await client.delete(`${COURSES}/${courseId}`)
}

/** Calls a course off with every part still to come. */
export async function cancelCourse(courseId: number, reason: string | null): Promise<CourseDetail> {
    return (await client.post<CourseDetail>(`${COURSES}/${courseId}/cancel`, {reason})).data
}

/** Replaces the sets of a course, in their new order. */
export async function replaceSets(courseId: number, sets: EventCourseSetDraft[]): Promise<CourseDetail> {
    return (await client.put<CourseDetail>(`${COURSES}/${courseId}/sets`, {sets})).data
}

/** Sets who may sign up for a course and who may see it. */
export async function setAudiences(
    courseId: number,
    registration: RestrictionAudience,
    view: RestrictionAudience,
): Promise<CourseDetail> {
    return (await client.put<CourseDetail>(`${COURSES}/${courseId}/audiences`, {registration, view})).data
}

/** Makes an existing one-time appointment a part of a course. */
export async function addPart(courseId: number, eventId: number, setId: number): Promise<EventCoursePart> {
    return (await client.post<EventCoursePart>(`${COURSES}/${courseId}/parts`, {eventId, setId})).data
}

/** Creates a new appointment straight into a course. */
export async function createPart(courseId: number, request: CoursePartCreateRequest): Promise<EventCoursePart> {
    return (await client.post<EventCoursePart>(`${COURSES}/${courseId}/parts/new`, request)).data
}

/** Moves a part into another set of its course. */
export async function movePart(courseId: number, eventId: number, setId: number): Promise<EventCoursePart> {
    return (await client.put<EventCoursePart>(`${COURSES}/${courseId}/parts/${eventId}`, {setId})).data
}

/** Makes a part an appointment of its own again. */
export async function removePart(courseId: number, eventId: number): Promise<void> {
    await client.delete(`${COURSES}/${courseId}/parts/${eventId}`)
}

/** Signs the reader, or somebody they look after, up with a choice of parts. */
export async function signUp(courseId: number, parts: number[], memberId?: number): Promise<CourseSignUp> {
    return (await client.post<CourseSignUp>(`${COURSES}/${courseId}/sign-up`, {parts, memberId})).data
}

/** Changes the parts chosen with a sign-up. */
export async function changeParts(courseId: number, parts: number[], memberId?: number): Promise<CourseSignUp> {
    return (await client.put<CourseSignUp>(`${COURSES}/${courseId}/sign-up`, {parts, memberId})).data
}

/** Withdraws a sign-up with every part chosen. */
export async function withdraw(courseId: number, memberId?: number): Promise<CourseSignUp> {
    return (await client.delete<CourseSignUp>(`${COURSES}/${courseId}/sign-up`, {params: {memberId}})).data
}

/** Every sign-up of a course that holds, waits for or was turned down for a place. */
export async function listSignUps(courseId: number): Promise<CourseSignUp[]> {
    return (await client.get<CourseSignUp[]>(`${COURSES}/${courseId}/sign-ups`)).data
}

/** Accepts or turns down a sign-up and every part chosen with it. */
export async function decide(courseId: number, memberId: number, status: RegistrationStatus): Promise<CourseSignUp> {
    return (await client.put<CourseSignUp>(`${COURSES}/${courseId}/sign-ups/${memberId}/status`, {status})).data
}

/** The course an appointment is a part of, or null for an appointment of its own. */
export async function courseOfEvent(eventId: number): Promise<CoursePartSummary | null> {
    const res = await client.get<CoursePartSummary | ''>(`/events/${eventId}/course`)
    return res.status === 204 || !res.data ? null : res.data
}

/** The courses still waiting on an answer from the reader or anybody they look after. */
export async function listAwaiting(): Promise<CourseAwaiting[]> {
    return (await client.get<CourseAwaiting[]>(`${COURSES}/awaiting`)).data
}
