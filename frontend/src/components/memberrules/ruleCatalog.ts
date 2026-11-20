/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {RuleField, RuleOperatorOption} from '@/api/generated/schema'

/** Something a check can name by id: a group or a tag of the station. */
export interface RuleTarget {
    id: number
    name: string
}

/** What the rule editor offers in its pickers, loaded once for the whole tree. */
export interface RuleCatalog {
    options: readonly RuleOperatorOption[]
    fields: readonly RuleField[]
    groups: readonly RuleTarget[]
    tags: readonly RuleTarget[]
    /** Whether the reader may read members, without which checks on member data are not offered. */
    readsMembers: boolean
    /** How deep a rule may go and how many checks it may hold, as the server keeps them. */
    limits: {depth: number; checks: number}
}
