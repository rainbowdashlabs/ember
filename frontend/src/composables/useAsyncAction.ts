/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Wraps a mutation (save, submit, delete, …) in the shared busy/error state machine so
 * views stop hand-rolling `saving = ref(false)` with try/catch/finally. `run` ignores
 * re-entrant calls while the action is in flight, resolves with the action's result, and
 * resolves with undefined when the action failed (the error lands in `error`).
 *
 * Dropping the second call is right for a submit button, where it is a double click and
 * sending it twice is the bug. It is wrong where several controls share one action: on a
 * ticket the title, the assignee and the due date all save the whole ticket, so leaving one
 * field and immediately changing another lost the second change without a word. Pass
 * `coalesce` there. The call made while the action is busy is then run once the current one
 * finishes, with the arguments it was given, which for a save of the whole object means the
 * last state wins and nothing is lost. Only the most recent waiting call is kept, because
 * running three identical saves in a row to catch up serves nobody.
 *
 * <p>A failure comes back two ways. `error` is the one line it always was, so every existing caller keeps
 * working. `failure` is the same thing described: what kind of failure it was, what the reader should do
 * about it, and whether it looks like a fault worth reporting. Render it with `FailureAlert` and the
 * reader gets the guidance and, where it is ours to fix, the way to report it.
 */
export function useAsyncAction<A extends unknown[], R>(
    fn: (...args: A) => Promise<R>,
    options?: {formatError?: (e: unknown) => string; coalesce?: boolean},
) {
    const {t} = useI18n()
    const running = ref(false)
    const error = ref('')
    const failure = ref<Failure | null>(null)
    let waiting: A | null = null

    async function run(...args: A): Promise<R | undefined> {
        if (running.value) {
            if (options?.coalesce) waiting = args
            return undefined
        }
        running.value = true
        error.value = ''
        failure.value = null
        try {
            return await fn(...args)
        } catch (e) {
            const described = describeFailure(e, t)
            failure.value = options?.formatError
                ? {...described, message: options.formatError(e)}
                : described
            error.value = failure.value.message
            return undefined
        } finally {
            running.value = false
            if (waiting) {
                const next = waiting
                waiting = null
                void run(...next)
            }
        }
    }

    function clearError() {
        error.value = ''
        failure.value = null
    }

    return {running: readonly(running), error: readonly(error), failure: readonly(failure), run, clearError}
}
