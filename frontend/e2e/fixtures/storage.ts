/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {expect} from './auth'

/**
 * The three storage services the end-to-end stack runs, and the small amount of ceremony a story needs to
 * prove that a switch moved anything.
 *
 * The one thing that catches everybody: inside the compose network the ports are the container ports and
 * not the ones mapped to the host. `sftp:22`, not `sftp:2222`. That mistake is made here once.
 */

/** The SFTP service, as the backend reaches it. */
export function sftpTarget() {
    return {
        type: 'SFTP' as const,
        host: 'sftp',
        port: 22,
        username: 'ember',
        knownHostsFingerprint: '',
        basePath: 'upload',
        password: 'ember',
        privateKey: '',
    }
}

/** The S3 service, as the backend reaches it. */
export function s3Target() {
    return {
        type: 'S3' as const,
        endpoint: 'http://rustfs:9000',
        region: 'us-east-1',
        bucket: 'ember',
        pathStyle: true,
        sseAlgorithm: '',
        basePath: '',
        accessKey: 'emberadmin',
        secretKey: 'emberadmin',
    }
}

/** The SMB service, as the backend reaches it. */
export function smbTarget() {
    return {
        type: 'SMB' as const,
        host: 'smb',
        port: 445,
        share: 'ember',
        domain: 'WORKGROUP',
        basePath: '',
        seal: false,
        dfs: false,
        username: 'ember',
        password: 'ember',
    }
}

/** A target nothing answers on, for the story about an apply that must move nothing. */
export function unreachableTarget() {
    return {...sftpTarget(), host: 'sftp-does-not-exist'}
}

/** One small file at a station, and what it takes to read it back. */
export interface StoredFile {
    hash: string
    bytes: string
}

/**
 * Uploads one small file at the station the given headers act for, through the ordinary upload path.
 *
 * Small on purpose: a move happens inside one request and the suite gives a story sixty seconds.
 */
export async function putSomething(
    request: APIRequestContext | Page['request'],
    headers: Record<string, string>,
    label: string,
): Promise<StoredFile> {
    const bytes = `storage-${label}-${Date.now()}`
    const uploaded = await request.post('/api/v1/media/files', {
        headers,
        multipart: {
            file: {name: `${label}.txt`, mimeType: 'text/plain', buffer: Buffer.from(bytes)},
        },
    })
    expect(uploaded.ok(), `the station could store a file (${uploaded.status()})`).toBeTruthy()
    const stored = await uploaded.json()
    return {hash: stored.contentHash, bytes}
}

/**
 * Reads a stored file back and hands over what came out.
 *
 * The whole point of every move story: a row that says "on the association's storage" says exactly the same
 * thing after a migration that copied nothing.
 */
export async function readBack(
    request: APIRequestContext | Page['request'],
    headers: Record<string, string>,
    file: StoredFile,
): Promise<string> {
    const response = await request.get(`/api/v1/media/file/${file.hash}`, {headers})
    expect(response.ok(), `the file is still readable (${response.status()})`).toBeTruthy()
    return response.text()
}

/** What an apply reports having carried, which is what says whether anything moved. */
export function movedCounts(result: {totalKeys: number; copied: number; deleted: number}) {
    return {total: result.totalKeys, copied: result.copied, deleted: result.deleted}
}
