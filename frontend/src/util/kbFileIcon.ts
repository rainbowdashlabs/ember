/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {KbFileType} from '@/api/knowledgeBase'

/**
 * Returns the FontAwesome icon tuple for a knowledge-base file based on its
 * type, with a generic file fallback for unknown and federated types.
 */
export function fileIcon(file: {fileType?: string}): string[] {
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
