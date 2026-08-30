/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client, {cancelTokenRefresh, scheduleTokenRefresh} from './client'
import {isStorageDenied, removeItem, setItem} from './storage'
import type {MessageResponse} from './types'

export interface LoginRequest {
    /** An email address or the name the account signs in with. */
    identifier?: string
    password?: string
    /**
     * Whether the person signing in vouches for this machine. Ticked, the session lasts as long as
     * the instance allows; left alone it lasts the short duration meant for a borrowed or shared
     * one. Says nothing about the second factor, which is a separate trust.
     */
    trustedDevice?: boolean
}

export interface LoginResponse {
    token?: string
    expiresAt?: string
    passwordChangeRequired: boolean
    passwordChangeToken?: string
    passwordChangeTokenExpiresAt?: string
    twoFactorRequired: boolean
    preAuthToken?: string
    preAuthTokenExpiresAt?: string
}

export interface RegisterRequest {
    email?: string
    firstName?: string
    lastName?: string
    password?: string
    registrationCode?: string
}

export interface RegisterResponse {
    id: number
    email?: string
    firstName?: string
    lastName?: string
    emailVerified: boolean
}

export interface TokenRequest {
    token?: string
}

export interface EmailRequest {
    email?: string
}

export const EmailChangeStatus = {
    COMMITTED: 'COMMITTED',
    WAITING: 'WAITING',
} as const

export type EmailChangeStatusName = (typeof EmailChangeStatus)[keyof typeof EmailChangeStatus]

export interface EmailChangeResponse {
    status: EmailChangeStatusName
    message: string
}

export interface SetPasswordRequest {
    token?: string
    password?: string
}

export interface SessionResponse {
    token?: string
    expiresAt?: string
}

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

export async function confirmEmailChange(data: TokenRequest): Promise<EmailChangeResponse> {
    const res = await client.post<EmailChangeResponse>('/auth/confirm-email-change', data)
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

export async function demoLogin(email: string): Promise<LoginResponse> {
    if (isStorageDenied()) {
        throw new StorageDeniedError()
    }
    const res = await client.post<LoginResponse>('/demo/login', {email})
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
    removeItem('cluster_id')
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

/** Whether a password link may still be used, and which of the two kinds it is. */
export interface PasswordLinkStatus {
    standing: 'VALID' | 'EXPIRED' | 'UNKNOWN'
    purpose: 'SETUP' | 'RESET' | 'OTHER'
}

/**
 * Asks what a link is worth before offering the form. Spends nothing, so a reader who reloads gets
 * the same answer.
 */
export async function passwordLinkStatus(token: string): Promise<PasswordLinkStatus> {
    const res = await client.post<PasswordLinkStatus>('/auth/password-link', {token})
    return res.data
}

/**
 * Sets the password a link was sent for, and signs in with it where nothing stands in the way.
 *
 * <p>Choosing the password proves the same thing as typing it into the sign-in form would, so the
 * server answers with a session rather than sending the person round to say it again. An account
 * with a second factor gets the same challenge the sign-in form would give it, and an answer with
 * neither means the password was set but the signing in has to be done by hand.
 */
export async function setPassword(data: SetPasswordRequest): Promise<LoginResponse> {
    const res = await client.post<LoginResponse>('/auth/set-password', data)
    if (res.data.token) {
        setItem('session_token', res.data.token)
        if (res.data.expiresAt) {
            setItem('session_expires_at', res.data.expiresAt)
            scheduleTokenRefresh(res.data.expiresAt)
        }
    }
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
