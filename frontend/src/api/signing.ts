/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {SealVerification} from '@/api/generated/schema'
import {uploadFile} from './upload'

/** The largest file the seal check takes, the same limit the server holds it to. */
export const SEAL_CHECK_MAX_BYTES = 25 * 1024 * 1024

/**
 * Checks the seals and timestamps of a PDF against this installation. Needs no session, and the
 * server keeps nothing of the file.
 *
 * @param file the PDF to check
 */
export async function verifySeals(file: File): Promise<SealVerification> {
    return uploadFile<SealVerification>('/public/signing/verify', {file})
}
