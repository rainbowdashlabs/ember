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
 * Answers whether the run it was handed to is still the latest one. A run that is no longer current
 * has been overtaken by a later `reload()` and must not write its answer anywhere.
 */
export type IsCurrentLoad = () => boolean

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
 * <p>A loader that runs on mount starts out loading, so the render before the mount already shows
 * the wait rather than an empty screen that fills a moment later.
 *
 * <p>Every run is numbered, and only the latest one may touch `loading`, `error` and `failure`. A
 * loader that a filter, a page or a route parameter re-runs can be overtaken by its own next run
 * while the first answer is still on its way, and the slower answer must not land last. The closure
 * is handed an {@link IsCurrentLoad} for that reason: it asks it after its last `await` and writes
 * nothing once it answers false.
 */
export function useAsyncLoader(
    fn: (isCurrent: IsCurrentLoad) => Promise<void>,
    options: UseAsyncLoaderOptions = {},
) {
    const {t} = useI18n()
    const loading = ref(options.autoLoad !== false)
    const error = ref('')
    const failure = ref<Failure | null>(null)
    let latestRun = 0

    async function reload() {
        const run = ++latestRun
        const isCurrent: IsCurrentLoad = () => run === latestRun
        loading.value = true
        error.value = ''
        failure.value = null
        try {
            await fn(isCurrent)
        } catch (e) {
            if (!isCurrent()) return
            const described = describeFailure(e, t)
            failure.value = options.errorMessageKey
                ? {...described, message: t(options.errorMessageKey)}
                : described
            error.value = failure.value.message
        } finally {
            if (isCurrent()) loading.value = false
        }
    }

    if (options.autoLoad !== false) {
        onMounted(reload)
    }

    return {loading, error, failure, reload}
}
