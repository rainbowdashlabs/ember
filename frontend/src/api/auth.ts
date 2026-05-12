/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client, {cancelTokenRefresh, scheduleTokenRefresh} from './client'
import {isStorageDenied, removeItem, setItem} from './storage'
import type {
    EmailRequest,
    LoginRequest,
    LoginResponse,
    MessageResponse,
    RegisterRequest,
    RegisterResponse,
    SessionResponse,
    SetPasswordRequest,
    TokenRequest,
} from './types'

export class StorageDeniedError extends Error {
    constructor() {
        super('Storage consent is required to log in')
        this.name = 'StorageDeniedError'
    }
}

export async function register(data: RegisterRequest): Promise<RegisterResponse> {
    const res = await client.post<RegisterResponse>('/auth/register', data)
    return res.data
}

export async function verifyEmail(data: TokenRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/auth/verify-email', data)
    return res.data
}

export async function login(data: LoginRequest): Promise<LoginResponse> {
    if (isStorageDenied()) {
        throw new StorageDeniedError()
    }
    const res = await client.post<LoginResponse>('/auth/login', data)
    if (res.data.token) {
        setItem('session_token', res.data.token)
        if (res.data.expiresAt) {
            setItem('session_expires_at', res.data.expiresAt)
            scheduleTokenRefresh(res.data.expiresAt)
        }
    }
    return res.data
}

export async function logout(data: TokenRequest): Promise<MessageResponse> {
    cancelTokenRefresh()
    const res = await client.post<MessageResponse>('/auth/logout', data)
    removeItem('session_token')
    removeItem('session_expires_at')
    removeItem('station_id')
    return res.data
}

export async function refresh(data: TokenRequest): Promise<SessionResponse> {
    const res = await client.post<SessionResponse>('/auth/refresh', data)
    if (res.data.token) {
        setItem('session_token', res.data.token)
    }
    return res.data
}

export async function forgotPassword(data: EmailRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/auth/forgot-password', data)
    return res.data
}

export async function setPassword(data: SetPasswordRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/auth/set-password', data)
    return res.data
}

export async function changePassword(data: { currentPassword: string; newPassword: string }): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/auth/change-password', data)
    return res.data
}

export async function resendVerification(data: EmailRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/auth/resend-verification', data)
    return res.data
}
