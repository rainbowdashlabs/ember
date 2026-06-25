/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'

interface UseAsyncLoaderOptions {
    /**
     * Auto-run on mount. Defaults to true. Set false for loaders that should only run
     * when explicitly triggered (e.g. on tab switch or after a route param appears).
     */
    autoLoad?: boolean
    /**
     * i18n key used as the fallback error message when the closure throws. Defaults to
     * `common.error`. Pass a different key for loaders whose surface phrasing should differ.
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
 */
export function useAsyncLoader(
    fn: () => Promise<void>,
    options: UseAsyncLoaderOptions = {},
) {
    const {t} = useI18n()
    const loading = ref(false)
    const error = ref('')
    const errorMessageKey = options.errorMessageKey ?? 'common.error'

    async function reload() {
        loading.value = true
        error.value = ''
        try {
            await fn()
        } catch {
            error.value = t(errorMessageKey)
        } finally {
            loading.value = false
        }
    }

    if (options.autoLoad !== false) {
        onMounted(reload)
    }

    return {loading, error, reload}
}
