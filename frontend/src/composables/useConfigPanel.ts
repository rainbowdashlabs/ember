/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {describeFailure, type Failure} from '@/util/failure'

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
    /**
     * The same failure described: what sort it was, what to do about it, and whether it looks like a
     * fault in Ember worth reporting. Render it with `FailureAlert` and the reader is told all three
     * instead of only that something went wrong.
     */
    failure: Ref<Failure | null>
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
    const failure = ref<Failure | null>(null)

    /**
     * Records what went wrong, described rather than swallowed.
     *
     * <p>A panel's own `formatError` names what could not be done and keeps that place, because it
     * knows the screen and the failure does not. Everything around it, what to do next and whether
     * this is a fault worth reporting, comes from the failure, which is the part no call site can
     * work out for itself.
     */
    function record(e: unknown) {
        const described = describeFailure(e, t)
        failure.value = options.formatError
            ? {...described, message: options.formatError(e)}
            : described
        error.value = failure.value.message
    }

    async function reload() {
        loading.value = true
        error.value = ''
        failure.value = null
        try {
            config.value = await options.fetch()
        } catch (e) {
            record(e)
        } finally {
            loading.value = false
        }
    }

    async function runWith(
        action: () => Promise<T>,
        runOptions?: {busy?: Ref<boolean>; rethrow?: boolean},
    ) {
        error.value = ''
        failure.value = null
        if (runOptions?.busy) runOptions.busy.value = true
        try {
            config.value = await action()
        } catch (e) {
            record(e)
            if (runOptions?.rethrow) throw e
        } finally {
            if (runOptions?.busy) runOptions.busy.value = false
        }
    }

    if (options.immediate !== false) {
        onMounted(reload)
    }

    return {config, loading, error, failure, reload, runWith}
}
