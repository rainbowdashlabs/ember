/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AllEventRestrictions,
    BreakRequest,
    CategoryRequest,
    EventBreak,
    EventCategory,
    EventRequest,
    EventRestrictions,
    StationEvent
} from './types'

// -- Events --

export async function listEvents(): Promise<StationEvent[]> {
    const res = await client.get<StationEvent[]>('/events')
    return res.data
}

export async function listTodayEvents(): Promise<StationEvent[]> {
    const res = await client.get<StationEvent[]>('/events/today')
    return res.data
}

export async function getEvent(id: number): Promise<StationEvent> {
    const res = await client.get<StationEvent>(`/events/${id}`)
    return res.data
}

export async function createEvent(data: EventRequest): Promise<StationEvent> {
    const res = await client.post<StationEvent>('/events', data)
    return res.data
}

export async function updateEvent(id: number, data: EventRequest): Promise<StationEvent> {
    const res = await client.put<StationEvent>(`/events/${id}`, data)
    return res.data
}

export async function deleteEvent(id: number): Promise<void> {
    await client.delete(`/events/${id}`)
}

// -- Categories --

export async function listCategories(): Promise<EventCategory[]> {
    const res = await client.get<EventCategory[]>('/events/categories')
    return res.data
}

export async function createCategory(data: CategoryRequest): Promise<EventCategory> {
    const res = await client.post<EventCategory>('/events/categories', data)
    return res.data
}

export async function updateCategory(id: number, data: CategoryRequest): Promise<unknown> {
    const res = await client.put(`/events/categories/${id}`, data)
    return res.data
}

export async function deleteCategory(id: number): Promise<void> {
    await client.delete(`/events/categories/${id}`)
}

// -- Registrations --

export interface EventRegistrationEntry {
    id: number
    eventId: number
    memberId: number
    memberName: string
    eventDate: string
    status: string  // RegistrationStatusName
    createdAt: string
}

export async function listMyRegistrations(): Promise<EventRegistrationEntry[]> {
    const res = await client.get<EventRegistrationEntry[]>('/events/registrations/mine')
    return res.data
}

export async function listPendingRegistrations(): Promise<EventRegistrationEntry[]> {
    const res = await client.get<EventRegistrationEntry[]>('/events/registrations/pending')
    return res.data
}

export async function getRestrictions(eventId: number): Promise<EventRestrictions> {
    const res = await client.get<EventRestrictions>(`/events/${eventId}/restrictions`)
    return res.data
}

export async function setRestrictions(eventId: number, data: EventRestrictions): Promise<EventRestrictions> {
    const res = await client.put<EventRestrictions>(`/events/${eventId}/restrictions`, data)
    return res.data
}

export async function listAllRestrictions(): Promise<AllEventRestrictions> {
    const res = await client.get<AllEventRestrictions>('/events/restrictions')
    return res.data
}

/**
 * Returns a map of eventId -> eligible memberIds (self + managed).
 * Events without restrictions are omitted (all eligible).
 */
export async function listEligibleMembers(): Promise<Record<number, number[]>> {
    const res = await client.get<Record<number, number[]>>('/events/eligible-members')
    return res.data
}

export async function registerForEvent(eventId: number, data: {
    eventDate?: string;
    memberId?: number
}): Promise<unknown> {
    const res = await client.post(`/events/${eventId}/register`, data)
    return res.data
}

export async function declineEvent(eventId: number, data: { eventDate?: string; memberId?: number }): Promise<unknown> {
    const res = await client.post(`/events/${eventId}/decline`, data)
    return res.data
}

export interface RegistrationCount {
    eventId: number
    eventDate: string
    status: string
    count: number
}

export async function listRegistrationCounts(): Promise<RegistrationCount[]> {
    const res = await client.get<RegistrationCount[]>('/events/registrations/counts')
    return res.data
}

export async function withdrawRegistration(id: number): Promise<void> {
    await client.delete(`/events/registrations/${id}`)
}

export async function updateRegistrationStatus(id: number, status: string): Promise<unknown> {
    const res = await client.put(`/events/registrations/${id}/status`, {status})
    return res.data
}

// -- Breaks --

// -- Field Defaults --

export interface EventFieldDefault {
    eventId: number
    fieldId: number
    source: string
    value?: string
}

export interface FieldDefaultEntry {
    fieldId: number
    source: string
    value?: string
}

export async function getFieldDefaults(eventId: number): Promise<EventFieldDefault[]> {
    const res = await client.get<EventFieldDefault[]>(`/events/${eventId}/field-defaults`)
    return res.data
}

export async function setFieldDefaults(eventId: number, data: FieldDefaultEntry[]): Promise<EventFieldDefault[]> {
    const res = await client.put<EventFieldDefault[]>(`/events/${eventId}/field-defaults`, data)
    return res.data
}

// -- Breaks --

export async function listBreaks(): Promise<EventBreak[]> {
    const res = await client.get<EventBreak[]>('/events/breaks')
    return res.data
}

export async function createBreak(data: BreakRequest): Promise<EventBreak> {
    const res = await client.post<EventBreak>('/events/breaks', data)
    return res.data
}

export async function updateBreak(id: number, data: BreakRequest): Promise<EventBreak> {
    const res = await client.put<EventBreak>(`/events/breaks/${id}`, data)
    return res.data
}

export async function deleteBreak(id: number): Promise<void> {
    await client.delete(`/events/breaks/${id}`)
}
