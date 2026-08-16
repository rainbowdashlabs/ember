/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {KbFileType} from '@/api/knowledgeBase'

/**
 * Tells whether a knowledge-base file has a written body that can be rendered as PDF. Mirrors the
 * file types the backend export route accepts, so no place offers a download the server refuses.
 */
export function isPdfExportable(fileType: string | undefined): boolean {
    return fileType === KbFileType.MARKDOWN || fileType === KbFileType.TEXT
}
