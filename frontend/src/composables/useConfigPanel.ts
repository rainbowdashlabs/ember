/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'

/**
 * State container returned by {@link useConfigPanel} - wraps a single remote
 * configuration object plus its load lifecycle. Panels typically destructure
 * `config`, `loading`, `error`, and `reload`, and use {@link runWith} to wrap
 * additional admin actions (e.g. save, regenerate secret) so they share the
 * same error and inline-loading conventions.
 */
export interface ConfigPanelState<T> {
    /** Reactive configuration object, seeded with {@link UseConfigPanelOptions.initial}. */
    config: Ref<T>
    /** True while the initial fetch (or an explicit {@link reload}) is in flight. */
    loading: Ref<boolean>
    /** Localised error message; empty string when no error is shown. */
    error: Ref<string>
    /** Refetches via {@link UseConfigPanelOptions.fetch} and resets `error`. */
    reload: () => Promise<void>
    /**
     * Runs an async admin action with shared error handling. The optional
     * `busy` ref is flipped while the action runs (useful for per-action
     * spinners like "generating"). Successful results replace `config`. The
     * optional `rethrow` flag re-throws caught errors after recording them so
     * callers like the save button can stay in their idle state.
     */
    runWith: (
        action: () => Promise<T>,
        options?: {busy?: Ref<boolean>; rethrow?: boolean},
    ) => Promise<void>
}

/**
 * Options for {@link useConfigPanel}.
 */
export interface UseConfigPanelOptions<T> {
    /** Seed value used until the first fetch resolves. */
    initial: T
    /** Async loader producing the panel's configuration object. */
    fetch: () => Promise<T>
    /** When true (default), fetches once on component mount. */
    immediate?: boolean
    /**
     * Optional custom error formatter invoked with the caught error. When
     * omitted, panels fall back to the translated `common.error` string.
     */
    formatError?: (e: unknown) => string
}

/**
 * Shared lifecycle for admin configuration panels: a single reactive `config`
 * object, a `loading` flag, an `error` string, and a `reload` action. The
 * returned `runWith` helper applies the same try/catch/finally shape to
 * panel-specific mutations (save, regenerate, …) so each panel stops
 * reimplementing the boilerplate.
 */
export function useConfigPanel<T>(options: UseConfigPanelOptions<T>): ConfigPanelState<T> {
    const {t} = useI18n()
    const config = ref(options.initial) as Ref<T>
    const loading = ref(true)
    const error = ref('')

    function describeError(e: unknown): string {
        return options.formatError ? options.formatError(e) : t('common.error')
    }

    async function reload() {
        loading.value = true
        error.value = ''
        try {
            config.value = await options.fetch()
        } catch (e) {
            error.value = describeError(e)
        } finally {
            loading.value = false
        }
    }

    async function runWith(
        action: () => Promise<T>,
        runOptions?: {busy?: Ref<boolean>; rethrow?: boolean},
    ) {
        error.value = ''
        if (runOptions?.busy) runOptions.busy.value = true
        try {
            config.value = await action()
        } catch (e) {
            error.value = describeError(e)
            if (runOptions?.rethrow) throw e
        } finally {
            if (runOptions?.busy) runOptions.busy.value = false
        }
    }

    if (options.immediate !== false) {
        onMounted(reload)
    }

    return {config, loading, error, reload, runWith}
}
