/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {describeFailure, type Failure} from '@/util/failure'

interface UseAsyncLoaderOptions {
    /**
     * Auto-run on mount. Defaults to true. Set false for loaders that should only run
     * when explicitly triggered (e.g. on tab switch or after a route param appears).
     */
    autoLoad?: boolean
    /**
     * i18n key naming what could not be fetched, for a loader whose own wording is better than the
     * one read off the failure. It replaces the sentence and nothing else: what to do about it, and
     * whether it is worth reporting, still come from the failure itself.
     */
    errorMessageKey?: string
}

/**
 * Generic async-load lifecycle wrapper. Owns `loading` and `error` reactives, runs the supplied
 * closure on mount (configurable) and on every `reload()` call, and absorbs the standard
 * try/catch/finally boilerplate so individual views can keep just the bespoke "assign results to
 * refs" body.
 *
 * Use over {@link useConfigPanel} when the view loads multiple resources into distinct refs, or
 * needs follow-up logic in the success branch.
 *
 * <p>A failure comes back two ways, as it does from {@link useAsyncAction}. `error` is the one line
 * it always was, so every existing caller keeps working. `failure` is the same thing described:
 * what sort of failure it was, what the reader should do about it, and whether it looks like a
 * fault in Ember worth reporting. Render that with `FailureAlert` and the reader is told all three.
 *
 * <p>It used to discard the thrown thing entirely, `catch` without even a binding, and write "that
 * did not work" over the top. Everything the server had said about why was thrown away at more than
 * a hundred call sites, which is most of the reason a reader of this application could not tell
 * their own mistake from ours.
 */
export function useAsyncLoader(
    fn: () => Promise<void>,
    options: UseAsyncLoaderOptions = {},
) {
    const {t} = useI18n()
    const loading = ref(false)
    const error = ref('')
    const failure = ref<Failure | null>(null)

    async function reload() {
        loading.value = true
        error.value = ''
        failure.value = null
        try {
            await fn()
        } catch (e) {
            const described = describeFailure(e, t)
            failure.value = options.errorMessageKey
                ? {...described, message: t(options.errorMessageKey)}
                : described
            error.value = failure.value.message
        } finally {
            loading.value = false
        }
    }

    if (options.autoLoad !== false) {
        onMounted(reload)
    }

    return {loading, error, failure, reload}
}
