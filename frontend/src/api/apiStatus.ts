/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface EndpointStats {
    method: string
    path: string
    requestCount: number
    avgDurationMs: number
    minDurationMs: number
    maxDurationMs: number
    errorRate: number
}

export interface StatusBreakdown {
    method: string
    path: string
    statusCode: number
    count: number
}

export interface HourlyStats {
    hour: string
    requestCount: number
    avgDurationMs: number
    errorCount: number
}

export async function getSlowest(limit = 20): Promise<EndpointStats[]> {
    const res = await client.get<EndpointStats[]>('/admin/api-status/slowest', {params: {limit}})
    return res.data
}

export async function getFastest(limit = 20): Promise<EndpointStats[]> {
    const res = await client.get<EndpointStats[]>('/admin/api-status/fastest', {params: {limit}})
    return res.data
}

export async function getFailing(limit = 20): Promise<EndpointStats[]> {
    const res = await client.get<EndpointStats[]>('/admin/api-status/failing', {params: {limit}})
    return res.data
}

export async function getStatusBreakdown(): Promise<StatusBreakdown[]> {
    const res = await client.get<StatusBreakdown[]>('/admin/api-status/status-breakdown')
    return res.data
}

export async function getHourlyStats(): Promise<HourlyStats[]> {
    const res = await client.get<HourlyStats[]>('/admin/api-status/hourly')
    return res.data
}

export interface RequestEntry {
    timestamp: string
    method: string
    path: string
    statusCode: number
    durationMs: number
}

export interface EndpointDetail {
    method: string
    path: string
    avgDurationMs: number
    requestCount: number
    statusCodes: { statusCode: number; count: number }[]
    recentRequests: RequestEntry[]
}

export async function getEndpointDetail(method: string, path: string): Promise<EndpointDetail> {
    const res = await client.get<EndpointDetail>('/admin/api-status/endpoint', {params: {method, path}})
    return res.data
}
