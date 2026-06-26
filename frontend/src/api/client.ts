/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios'
import {getItem, removeItem, setItem} from './storage'
import {showToast} from '@/util/toast'
import {reportApiError} from '@/util/devErrorReporter'
import {requestStepUp, type StepUpCategory} from '@/util/stepUp'

// -- Request history for problem reports --
interface RequestHistoryEntry {
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

function waitForRefresh(config: InternalAxiosRequestConfig): Promise<InternalAxiosRequestConfig> {
    return new Promise((resolve) => {
        refreshQueue.push(() => {
            config.headers.Authorization = `Bearer ${getItem('session_token')}`
            resolve(config)
        })
    })
}

function releaseQueue() {
    refreshQueue.forEach((cb) => cb({} as InternalAxiosRequestConfig))
    refreshQueue = []
}

client.interceptors.request.use((config) => {
    (config as any)._startTime = Date.now();
    // If refreshing and this isn't the refresh request itself, wait
    if (refreshing && !config.url?.includes('/auth/refresh')) {
        return waitForRefresh(config)
    }

    const token = getItem('session_token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    const stationId = getItem('station_id')
    if (stationId) {
        config.headers['X-Station-Id'] = stationId
    }
    return config
})

client.interceptors.response.use(
    (response) => {
        const start = (response.config as any)._startTime
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
            const start = (config as any)._startTime
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
            const body: any = error.response?.data
            const isStepUp = body?.error === 'step_up_required'
                || error.response?.headers?.['x-stepup-required'] != null
            if (isStepUp && config && !(config as any)._stepUpRetried) {
                const category = (body?.category
                    ?? error.response?.headers?.['x-stepup-required']) as StepUpCategory
                ;(config as any)._stepUpRetried = true
                return requestStepUp(category)
                    .then(() => client.request(config))
                    .catch((stepUpErr) => Promise.reject(stepUpErr ?? (error as AxiosError)))
            }
            const token = getItem('session_token')
            if (token && !refreshing && !isStepUp) {
                removeItem('session_token')
                removeItem('station_id')
                const currentPath = window.location.pathname + window.location.search
                const redirect = currentPath && currentPath !== '/' && currentPath !== '/login'
                    ? `?redirect=${encodeURIComponent(currentPath)}`
                    : ''
                window.location.href = '/login' + redirect
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
            // Refresh failed — session will expire, 401 interceptor will handle redirect
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
