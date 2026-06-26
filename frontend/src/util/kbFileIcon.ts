/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {KbFile} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'

/**
 * Returns the FontAwesome icon tuple for a knowledge-base file based on its
 * type, with a generic file fallback for unknown types.
 */
export function fileIcon(file: KbFile): string[] {
    switch (file.fileType) {
        case KbFileType.MARKDOWN:
        case KbFileType.TEXT:
            return ['fas', 'file-lines']
        case KbFileType.PDF:
            return ['fas', 'file-pdf']
        case KbFileType.IMAGE:
            return ['fas', 'image']
        case KbFileType.YOUTUBE:
            return ['fab', 'youtube']
        case KbFileType.LINK:
            return ['fas', 'link']
        case KbFileType.PRESENTATION:
            return ['fas', 'file-powerpoint']
        default:
            return ['fas', 'file']
    }
}
