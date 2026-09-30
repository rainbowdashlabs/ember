/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {isStorageDenied, removeItem} from './storage'
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
    /** When the session ends. Present only where the answer started one, which then sits in a cookie. */
    expiresAt?: string
    passwordChangeRequired: boolean
    passwordChangeToken?: string
    passwordChangeTokenExpiresAt?: string
    /**
     * Whether the account administers the instance and carries no address that can be written to.
     * There is no session until it has one, and the token below is what the step is spent with.
     */
    addressRequired: boolean
    addressToken?: string
    addressTokenExpiresAt?: string
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

export interface SetAddressRequest {
    token?: string
    email?: string
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

/**
 * Whether a sign-in answer is a finished session rather than a step still owed. The session itself
 * arrived as a cookie the page cannot read; the answer only says that it did.
 */
export function startedSession(res: LoginResponse): boolean {
    return !!res.expiresAt && !res.passwordChangeRequired && !res.addressRequired && !res.twoFactorRequired
}

export async function login(data: LoginRequest): Promise<LoginResponse> {
    if (isStorageDenied()) {
        throw new StorageDeniedError()
    }
    const res = await client.post<LoginResponse>('/auth/login', data)
    return res.data
}

export async function demoLogin(email: string): Promise<LoginResponse> {
    if (isStorageDenied()) {
        throw new StorageDeniedError()
    }
    const res = await client.post<LoginResponse>('/demo/login', {email})
    return res.data
}

/** Ends the session the cookie names; the server clears the cookie with its answer. */
export async function logout(): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/auth/logout')
    removeItem('station_id')
    removeItem('cluster_id')
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
    return res.data
}

/**
 * Puts a reachable address on an account that a sign-in stopped for one, and signs in with it.
 *
 * <p>Answers exactly as setting a password does, because it is the other half of the same forced
 * first step: a session where nothing else is owed, a second factor to give where there is one.
 */
export async function setAddress(data: SetAddressRequest): Promise<LoginResponse> {
    const res = await client.post<LoginResponse>('/auth/set-address', data)
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
