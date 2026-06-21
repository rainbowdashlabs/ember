/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

export type StepUpCategory =
    | 'ACCOUNT_SECURITY'
    | 'FEDERATION'
    | 'INSTANCE_CONFIG'
    | 'ROLE_CHANGE'

interface PendingPrompt {
    category: StepUpCategory
    resolve: () => void
    reject: (reason?: unknown) => void
}

const current = ref<PendingPrompt | null>(null)

/**
 * Opens the step-up modal for the given category and resolves once the user has completed
 * the 2FA challenge. Rejects when the user cancels the prompt.
 *
 * Calls made while a prompt is already in flight share the same outcome — this prevents a
 * burst of parallel ACCOUNT_SECURITY requests from stacking multiple modals on top of each
 * other.
 */
export function requestStepUp(category: StepUpCategory): Promise<void> {
    if (current.value) {
        const existing = current.value
        return new Promise<void>((resolve, reject) => {
            const original = existing.resolve
            const originalReject = existing.reject
            existing.resolve = () => {
                original()
                resolve()
            }
            existing.reject = (r) => {
                originalReject(r)
                reject(r)
            }
        })
    }
    return new Promise<void>((resolve, reject) => {
        current.value = {category, resolve, reject}
    })
}

export function useStepUpPrompt() {
    return {
        current,
        complete: () => {
            const prompt = current.value
            current.value = null
            prompt?.resolve()
        },
        cancel: () => {
            const prompt = current.value
            current.value = null
            prompt?.reject(new Error('Step-up cancelled'))
        },
    }
}
