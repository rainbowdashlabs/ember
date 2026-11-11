/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {beacon, problems} from '@/api'
import {ProblemLevel, ProblemState, type StoredProblem} from '@/api/generated/schema'
import type {ProblemStateFilter} from '@/api/problems'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useMonitoringCounts} from '@/composables/useMonitoringCounts'
import {describeFailure, type Failure} from '@/util/failure'

/** The changes of state a card offers, each by the call that makes it. */
const STATE_CHANGES = {
    resolve: problems.resolve,
    mute: problems.mute,
    unmute: problems.unmute,
} as const

/** A change of state a card offers. */
export type ProblemStateChange = keyof typeof STATE_CHANGES

/**
 * The problem log as the administration works through it: the faults or the refusals, in one state,
 * narrowed by level, changed one at a time or all at once, and picked for the beacon.
 *
 * <p>The list is loaded again after every change, without the spinner, so the reader keeps their place
 * in it. Only the answer to the latest request is shown: a reader switching filters faster than the
 * server answers never sees the list of a filter they already left. Whatever fails is said, and the
 * list is loaded again all the same, so it shows what the server holds rather than what was hoped for.
 */
export function useProblemList() {
    const {t} = useI18n()
    const {refresh: refreshCounts} = useMonitoringCounts()

    const refusals = ref(false)
    const state = ref<ProblemStateFilter>(ProblemState.OPEN)
    const level = ref<ProblemLevel | null>(null)
    const entries = ref<StoredProblem[]>([])
    const loading = ref(true)
    const failure = ref<Failure | null>(null)
    const result = ref('')
    const selected = ref<Set<number>>(new Set())
    const latestRequest = ref(0)

    const visible = computed(() =>
        level.value == null ? entries.value : entries.value.filter(e => e.level === level.value))
    const openFaults = computed(() => entries.value.filter(e => e.state === ProblemState.OPEN))
    const errorCount = computed(() => openFaults.value.filter(e => e.level === ProblemLevel.ERROR).length)
    const warnCount = computed(() => openFaults.value.filter(e => e.level === ProblemLevel.WARN).length)
    /** Resolve-all reaches every open problem of the kind, so it is offered only where all of them are shown. */
    const canResolveAll = computed(() => level.value == null && visible.value.some(e => e.state === ProblemState.OPEN))

    async function load(quietly = false) {
        const request = latestRequest.value + 1
        latestRequest.value = request
        if (!quietly) loading.value = true
        try {
            const listed = await problems.listProblems(state.value, refusals.value)
            if (request !== latestRequest.value) return
            entries.value = listed
            selected.value = new Set([...selected.value].filter(id => listed.some(e => e.id === id)))
        } catch (e) {
            if (request === latestRequest.value) failure.value = describeFailure(e, t)
        } finally {
            if (request === latestRequest.value) loading.value = false
        }
    }

    function startOver() {
        entries.value = []
        selected.value = new Set()
        result.value = ''
        failure.value = null
        void load()
    }

    function showKind(showRefusals: boolean) {
        refusals.value = showRefusals
        level.value = null
        startOver()
    }

    function showState(next: ProblemStateFilter) {
        state.value = next
        startOver()
    }

    function toggleLevel(next: ProblemLevel) {
        level.value = level.value === next ? null : next
    }

    async function settled() {
        await load(true)
        void refreshCounts()
    }

    async function run(action: () => Promise<void>) {
        failure.value = null
        result.value = ''
        try {
            await action()
        } catch (e) {
            failure.value = describeFailure(e, t)
        } finally {
            await settled()
        }
    }

    function change(action: ProblemStateChange, id: number) {
        return run(() => STATE_CHANGES[action](id))
    }

    function resolveAll() {
        return run(async () => {
            const {resolved} = await problems.resolveAll(refusals.value)
            result.value = t('adminProblems.resolvedCount', {count: resolved})
        })
    }

    function toggleSelected(id: number) {
        const next = new Set(selected.value)
        if (next.has(id)) next.delete(id)
        else next.add(id)
        selected.value = next
    }

    function sendSelected() {
        if (selected.value.size === 0) return Promise.resolve()
        return run(async () => {
            const queued = await beacon.sendProblems([...selected.value])
            result.value = t('beacon.queued', {count: queued})
            selected.value = new Set()
        })
    }

    const deletion = useConfirmDelete<StoredProblem>({
        onDelete: entry => problems.deleteProblem(entry.id),
        onSuccess: settled,
        failure,
    })

    function requestDelete(id: number) {
        const entry = entries.value.find(e => e.id === id)
        if (entry) deletion.requestDelete(entry)
    }

    onMounted(() => load())

    return {
        refusals, state, level, entries, visible, loading, failure, result, selected,
        errorCount, warnCount, canResolveAll,
        showKind, showState, toggleLevel, change, resolveAll, toggleSelected, sendSelected, requestDelete,
        deletion,
    }
}
