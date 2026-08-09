/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {useI18n} from 'vue-i18n'

/**
 * Wraps a mutation (save, submit, delete, …) in the shared busy/error state machine so
 * views stop hand-rolling `saving = ref(false)` with try/catch/finally. `run` ignores
 * re-entrant calls while the action is in flight, resolves with the action's result, and
 * resolves with undefined when the action failed (the error lands in `error`).
 */
export function useAsyncAction<A extends unknown[], R>(
    fn: (...args: A) => Promise<R>,
    options?: {formatError?: (e: unknown) => string},
) {
    const {t} = useI18n()
    const running = ref(false)
    const error = ref('')

    async function run(...args: A): Promise<R | undefined> {
        if (running.value) return undefined
        running.value = true
        error.value = ''
        try {
            return await fn(...args)
        } catch (e) {
            const message = (e as {response?: {data?: {message?: string}}})?.response?.data?.message
            error.value = options?.formatError?.(e) ?? message ?? t('common.error')
            return undefined
        } finally {
            running.value = false
        }
    }

    function clearError() {
        error.value = ''
    }

    return {running: readonly(running), error: readonly(error), run, clearError}
}
