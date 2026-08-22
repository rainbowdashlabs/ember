/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios'

declare module 'axios' {
    export interface InternalAxiosRequestConfig {
        _startTime?: number
        _stepUpRetried?: boolean
    }
}
import {getItem, removeItem, setItem} from './storage'
import {showToast} from '@/util/toast'
import {reportApiError} from '@/util/devErrorReporter'
import {requestStepUp, type StepUpCategory} from '@/util/stepUp'
import type {ApiErrorBody} from '@/util/apiError'
import {getActingStation} from '@/util/actingStationState'

// -- Request history for problem reports --
export interface RequestHistoryEntry {
    method: string
    url: string
    status: number | null
    duration: number
    timestamp: string
    error?: string
}

const requestHistory: RequestHistoryEntry[] = []
const MAX_HISTORY = 20

export function getRequestHistory(): RequestHistoryEntry[] {
    return [...requestHistory]
}

const client = axios.create({
    baseURL: '/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
})

// -- Refresh gate: block requests while token is being refreshed --

let refreshing = false
let refreshQueue: Array<(config: InternalAxiosRequestConfig) => void> = []

function applyAuthHeaders(config: InternalAxiosRequestConfig) {
    const token = getItem('session_token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    // A screen that edits an association's own content acts at the station the association owns, whether or
    // not the reader has a station of their own selected.
    const stationId = getActingStation() ?? getItem('station_id')
    if (stationId) {
        config.headers['X-Station-Id'] = stationId
    }
    // A request may carry both: one person can be a cluster manager and a member of one of its stations
    const clusterId = getItem('cluster_id')
    if (clusterId) {
        config.headers['X-Cluster-Id'] = clusterId
    }
}

function waitForRefresh(config: InternalAxiosRequestConfig): Promise<InternalAxiosRequestConfig> {
    return new Promise((resolve) => {
        refreshQueue.push(() => {
            applyAuthHeaders(config)
            resolve(config)
        })
    })
}

function releaseQueue() {
    refreshQueue.forEach((cb) => cb({} as InternalAxiosRequestConfig))
    refreshQueue = []
}

client.interceptors.request.use((config) => {
    config._startTime = Date.now()
    // If refreshing and this isn't the refresh request itself, wait
    if (refreshing && !config.url?.includes('/auth/refresh')) {
        return waitForRefresh(config)
    }

    applyAuthHeaders(config)
    return config
})

client.interceptors.response.use(
    (response) => {
        const start = response.config._startTime
        requestHistory.push({
            method: (response.config.method ?? 'GET').toUpperCase(),
            url: response.config.url ?? '',
            status: response.status,
            duration: start ? Date.now() - start : 0,
            timestamp: new Date().toISOString(),
        })
        if (requestHistory.length > MAX_HISTORY) requestHistory.shift()
        return response
    },
    (error) => {
        const config = error?.config
        if (config) {
            const start = config._startTime
            requestHistory.push({
                method: (config.method ?? 'GET').toUpperCase(),
                url: config.url ?? '',
                status: error?.response?.status ?? null,
                duration: start ? Date.now() - start : 0,
                timestamp: new Date().toISOString(),
                error: error?.response?.data?.message ?? error?.message,
            })
            if (requestHistory.length > MAX_HISTORY) requestHistory.shift()
        }
        const status = error?.response?.status
        if (status && status !== 401 && status !== 403) {
            reportApiError(
                config?.method ?? 'GET',
                config?.url ?? '',
                status,
                error?.response?.data?.message ?? error?.message ?? '',
            )
        }
        if (error.response?.status === 401) {
            const body = error.response?.data as ApiErrorBody | undefined
            const isStepUp = body?.error === 'step_up_required'
                || error.response?.headers?.['x-stepup-required'] != null
            if (isStepUp && config && !config._stepUpRetried) {
                const category = (body?.category
                    ?? error.response?.headers?.['x-stepup-required']) as StepUpCategory
                config._stepUpRetried = true
                return requestStepUp(category)
                    .then(() => client.request(config))
                    .catch((stepUpErr) => Promise.reject(stepUpErr ?? (error as AxiosError)))
            }
            const token = getItem('session_token')
            if (token && !refreshing && !isStepUp) {
                removeItem('session_token')
                removeItem('station_id')
                removeItem('cluster_id')
                const currentPath = window.location.pathname
                const isPublicPath = currentPath === '/'
                    || currentPath === '/login'
                    || currentPath === '/2fa-verify'
                    || currentPath.startsWith('/helpcenter')
                if (!isPublicPath) {
                    const fullPath = currentPath + window.location.search
                    window.location.href = '/login?redirect=' + encodeURIComponent(fullPath)
                }
            }
        }
        if (error.response?.status === 403) {
            const message = error.response?.data?.message ?? 'Kein Zugriff auf diesen Inhalt.'
            showToast(message, 'error')
        }
        return Promise.reject(error)
    },
)

// -- Token refresh --

let refreshTimer: ReturnType<typeof setTimeout> | null = null

export function scheduleTokenRefresh(expiresAt: string) {
    cancelTokenRefresh()
    const expiryMs = new Date(expiresAt).getTime()
    const now = Date.now()
    // Refresh 2 minutes before expiry
    const delay = Math.max(expiryMs - now - 2 * 60 * 1000, 10_000)

    refreshTimer = setTimeout(async () => {
        const token = getItem('session_token')
        if (!token) return

        refreshing = true
        try {
            const res = await client.post<{ token?: string; expiresAt?: string }>('/auth/refresh', {token})
            if (res.data.token) {
                setItem('session_token', res.data.token)
                if (res.data.expiresAt) {
                    setItem('session_expires_at', res.data.expiresAt)
                    scheduleTokenRefresh(res.data.expiresAt)
                }
            }
        } catch {
            // Refresh failed - session will expire, 401 interceptor will handle redirect
        } finally {
            refreshing = false
            releaseQueue()
        }
    }, delay)
}

export function cancelTokenRefresh() {
    if (refreshTimer !== null) {
        clearTimeout(refreshTimer)
        refreshTimer = null
    }
}

export function initTokenRefresh() {
    const token = getItem('session_token')
    const expiresAt = getItem('session_expires_at')
    if (token && expiresAt) {
        scheduleTokenRefresh(expiresAt)
    }
}

export default client
