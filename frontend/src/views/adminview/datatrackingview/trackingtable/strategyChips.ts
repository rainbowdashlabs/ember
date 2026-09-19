/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {DataTracking, TableEntry} from '@/api/dataTracking'

/** One deletion strategy a table carries, and for a cascade, what the table it cascades from does. */
export interface StrategyChip {
    strategy: string
    column: string
    reason: string
    cascadeFrom?: {table: string; effective: string}
}

/** The strategies from the one that removes the most to the one that removes nothing. */
const STRATEGY_STRENGTH = ['DELETE_EXPLICIT', 'CASCADE', 'NULL', 'ANONYMIZE', 'RETAIN_UNLINKED', 'RETAIN', 'NOT_APPLICABLE']

/** What a parent strategy leaves a cascade to fire on: nothing, because the parent row stays. */
const NON_DELETING = ['ANONYMIZE', 'RETAIN', 'RETAIN_UNLINKED', 'NOT_APPLICABLE', 'NONE']

function strongestOf(strategies: readonly string[]): string {
    return strategies.reduce((best, strategy) =>
        STRATEGY_STRENGTH.indexOf(strategy) < STRATEGY_STRENGTH.indexOf(best) ? strategy : best)
}

function cascadeParentOf(entry: TableEntry, column: string, tracking: DataTracking | null): StrategyChip['cascadeFrom'] {
    const fk = entry.foreignKeys?.find(candidate => candidate.column === column)
    if (!fk || !tracking) return undefined
    const parent = tracking.tables[fk.refTable]
    if (!parent) return {table: fk.refTable, effective: 'unknown'}
    const parentStrategies = (parent.gdprDeletion?.strategies ?? []).map(strategy => strategy.strategy)
    return {table: fk.refTable, effective: parentStrategies.length === 0 ? 'NONE' : strongestOf(parentStrategies)}
}

/** The distinct deletion strategies of one table, each cascade with the parent it hangs on. */
export function strategyChipsOf(entry: TableEntry, tracking: DataTracking | null): StrategyChip[] {
    const seen = new Set<string>()
    const chips: StrategyChip[] = []
    for (const strategy of entry.gdprDeletion?.strategies ?? []) {
        const key = `${strategy.strategy}|${strategy.column}`
        if (!strategy.strategy || seen.has(key)) continue
        seen.add(key)
        const chip: StrategyChip = {strategy: strategy.strategy, column: strategy.column, reason: strategy.reason ?? ''}
        if (strategy.strategy === 'CASCADE') chip.cascadeFrom = cascadeParentOf(entry, strategy.column, tracking)
        chips.push(chip)
    }
    return chips
}

/** Whether a cascade will never fire, because the table it hangs on is never deleted from. */
export function isCascadeMisleading(chip: StrategyChip): boolean {
    if (chip.strategy !== 'CASCADE' || !chip.cascadeFrom) return false
    return NON_DELETING.includes(chip.cascadeFrom.effective)
}

export function strategyClasses(strategy: string): string {
    switch (strategy) {
        case 'CASCADE':
        case 'DELETE_EXPLICIT':
            return 'bg-(--bg-accent) text-(--text-muted) border border-(--border)'
        case 'ANONYMIZE':
            return 'bg-[#73CEFF]/15 text-[#3694FF] border border-[#73CEFF]/40'
        case 'NULL':
            return 'bg-[#ffdd1b]/15 text-[#a07a00] border border-[#ffdd1b]/50 dark:text-[#ffdd1b]'
        case 'RETAIN':
            return 'bg-[#ec2929]/15 text-[#ec2929] border border-[#ec2929]/40'
        case 'RETAIN_UNLINKED':
            return 'bg-[#ec2929]/10 text-[#ec2929] border border-[#ec2929]/30'
        default:
            return 'bg-(--bg-accent) text-(--text-muted) border border-(--border) opacity-60'
    }
}
