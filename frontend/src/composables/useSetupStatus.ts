/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {setup} from '@/api'
import type {SetupStatus, SetupStep} from '@/api/setup'

const status = ref<SetupStatus | null>(null)
const loading = ref(false)
const loaded = ref(false)

export function useSetupStatus() {
    async function load(force = false) {
        if (loaded.value && !force) return
        loading.value = true
        try {
            status.value = await setup.getStatus()
            loaded.value = true
        } finally {
            loading.value = false
        }
    }

    async function reload() {
        await load(true)
    }

    async function markComplete(): Promise<{ok: true; status: SetupStatus} | {ok: false; missingSteps: string[]}> {
        try {
            const result = await setup.complete()
            status.value = result
            return {ok: true, status: result}
        } catch (e: unknown) {
            const err = e as {response?: {status?: number; data?: {missingSteps?: string[]}}}
            if (err.response?.status === 409 && err.response.data?.missingSteps) {
                return {ok: false, missingSteps: err.response.data.missingSteps}
            }
            throw e
        }
    }

    const completedAt = computed(() => status.value?.completedAt ?? null)
    const requiredSteps = computed<SetupStep[]>(() => status.value?.requiredSteps ?? [])
    const optionalSteps = computed<SetupStep[]>(() => status.value?.optionalSteps ?? [])

    const allRequiredDone = computed(
        () => requiredSteps.value.length > 0 && requiredSteps.value.every((s) => s.complete),
    )

    const firstIncompleteRequiredId = computed(
        () => requiredSteps.value.find((s) => !s.complete)?.id ?? null,
    )

    const hasIncompleteApplicable = computed(
        () =>
            requiredSteps.value.some((s) => !s.complete) ||
            optionalSteps.value.some((s) => s.applicable && !s.complete),
    )

    return {
        status,
        loading,
        loaded,
        completedAt,
        requiredSteps,
        optionalSteps,
        allRequiredDone,
        firstIncompleteRequiredId,
        hasIncompleteApplicable,
        load,
        reload,
        markComplete,
    }
}
