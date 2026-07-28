/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    S3Request,
    S3Summary,
    SftpRequest,
    SftpSummary,
    SmbRequest,
    SmbSummary,
} from '@/api/storageBackend'

/** Blank S3 form state. */
export function newS3(): S3Request {
    return {
        type: 'S3',
        endpoint: '',
        region: '',
        bucket: '',
        pathStyle: false,
        sseAlgorithm: '',
        basePath: '',
        accessKey: '',
        secretKey: '',
    }
}

/** Blank SMB form state. */
export function newSmb(): SmbRequest {
    return {
        type: 'SMB',
        host: '',
        port: 445,
        share: '',
        domain: '',
        basePath: '',
        seal: true,
        dfs: false,
        username: '',
        password: '',
    }
}

/** Blank SFTP form state. */
export function newSftp(): SftpRequest {
    return {
        type: 'SFTP',
        host: '',
        port: 22,
        username: '',
        knownHostsFingerprint: '',
        basePath: '',
        password: '',
        privateKey: '',
    }
}

/**
 * Seeds S3 form state from a stored backend summary. Credentials stay blank —
 * the server never echoes them back.
 */
export function s3FormFrom(summary: S3Summary): S3Request {
    return {
        type: 'S3',
        endpoint: summary.endpoint,
        region: summary.region,
        bucket: summary.bucket,
        pathStyle: summary.pathStyle,
        sseAlgorithm: summary.sseAlgorithm ?? '',
        basePath: summary.basePath,
        accessKey: '',
        secretKey: '',
    }
}

/**
 * Seeds SMB form state from a stored backend summary. Credentials stay blank —
 * the server never echoes them back.
 */
export function smbFormFrom(summary: SmbSummary): SmbRequest {
    return {
        type: 'SMB',
        host: summary.host,
        port: summary.port,
        share: summary.share,
        domain: summary.domain ?? '',
        basePath: summary.basePath,
        seal: summary.seal,
        dfs: summary.dfs,
        username: '',
        password: '',
    }
}

/**
 * Seeds SFTP form state from a stored backend summary. The host fingerprint and
 * the credentials stay blank — the server never echoes them back.
 */
export function sftpFormFrom(summary: SftpSummary): SftpRequest {
    return {
        type: 'SFTP',
        host: summary.host,
        port: summary.port,
        username: summary.username,
        knownHostsFingerprint: '',
        basePath: summary.basePath,
        password: '',
        privateKey: '',
    }
}
