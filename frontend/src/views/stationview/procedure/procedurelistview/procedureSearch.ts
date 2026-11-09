/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Procedure} from '@/api/generated/schema'

/**
 * Whether a procedure matches what was typed into the search: its name or its description holds it,
 * whatever the case. An empty search matches every procedure.
 *
 * @param procedure the procedure
 * @param query     what was typed, untrimmed
 */
export function matchesSearch(procedure: Pick<Procedure, 'name' | 'description'>, query: string): boolean {
    const wanted = query.trim().toLowerCase()
    if (!wanted) return true
    return procedure.name.toLowerCase().includes(wanted)
        || (procedure.description?.toLowerCase().includes(wanted) ?? false)
}
