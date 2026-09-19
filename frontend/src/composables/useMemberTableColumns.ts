/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import type {MemberTableColumn, MemberTableHeader} from '@/api/memberTable'
import type {ExportFieldOption} from '@/composables/useExport'

/**
 * The columns a picker offers, and which of them are ticked.
 *
 * <p>A picker needs one string per option and a column is three things, so each is written as one:
 * the kind, and then either the builtin's name or the question's id. Nothing is read back out of
 * these strings except here, and the server is told the columns rather than the strings.
 */
export function useMemberTableColumns() {
    const offered = ref<MemberTableHeader[]>([])
    const selected = ref<Set<string>>(new Set())

    /** The one string that stands for a column, unique across the three kinds. */
    function keyOf(column: MemberTableHeader | MemberTableColumn): string {
        if (column.kind === 'BUILTIN') return `b:${column.key}`
        return column.kind === 'PROFILE_FIELD' ? `p:${column.fieldId}` : `q:${column.fieldId}`
    }

    function columnOf(key: string): MemberTableColumn | null {
        const [kind, rest] = [key.slice(0, 1), key.slice(2)]
        if (kind === 'b') return {kind: 'BUILTIN', key: rest, fieldId: null}
        const fieldId = Number(rest)
        if (!Number.isFinite(fieldId)) return null
        return {kind: kind === 'p' ? 'PROFILE_FIELD' : 'REGISTRATION_FIELD', key: null, fieldId}
    }

    const options = computed<ExportFieldOption[]>(() =>
        offered.value.map(column => ({key: keyOf(column), label: column.label})))

    /** The ticked columns, in the order they were offered, which is the order they are printed. */
    const columns = computed<MemberTableColumn[]>(() =>
        offered.value
            .filter(column => selected.value.has(keyOf(column)))
            .map(column => columnOf(keyOf(column)))
            .filter((column): column is MemberTableColumn => column !== null))

    function toggle(key: string) {
        const next = new Set(selected.value)
        if (next.has(key)) next.delete(key)
        else next.add(key)
        selected.value = next
    }

    function select(keys: string[]) {
        selected.value = new Set(keys)
    }

    /**
     * Ticks a saved selection, keeping only what this reader was actually offered.
     *
     * <p>A selection may name a question the reader may not read: that is not a broken selection and
     * never was, so the rest of it is ticked rather than the whole thing refused.
     */
    function apply(saved: MemberTableColumn[]) {
        const offerable = new Set(offered.value.map(keyOf))
        selected.value = new Set(saved.map(keyOf).filter(key => offerable.has(key)))
    }

    return {offered, selected, options, columns, keyOf, toggle, select, apply}
}
