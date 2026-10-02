/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {KbVersionResponse} from '@/api/generated/schema'

/** Three saved versions of one wiki file, newest first, as the versions page lists them. */
export const sampleVersions: KbVersionResponse[] = [
    {id: 3, version: 3, isFull: false, createdAt: '2026-03-09T17:40:00Z', createdBy: 1, createdByName: 'Max Mustermann'},
    {id: 2, version: 2, isFull: false, createdAt: '2026-03-08T19:15:00Z', createdBy: 2, createdByName: 'Lisa Beispiel'},
    {id: 1, version: 1, isFull: true, createdAt: '2026-03-02T18:00:00Z', createdBy: 1, createdByName: 'Max Mustermann'},
]
