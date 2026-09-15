/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProfileField} from '@/api/profileFields'

/**
 * A question as one audience meets it, which is what the order list and the preview both draw.
 *
 * <p>Width, whether an answer is expected and whether it may be written are that audience's answers
 * rather than the question's, so they stand here as plain values with nothing left to resolve.
 */
export type AskedField = ProfileField & {
    required: boolean
    width: string | null
    readonly: boolean
    position: number
}
