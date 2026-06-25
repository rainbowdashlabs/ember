/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'

/**
 * Reactive state returned by {@link useConfirmAction}. Views typically bind `show`
 * to a modal's `v-model`, render `target.value` inside the confirmation body, wire
 * `request` to the trigger button, and pass `confirm` to the modal's confirm handler.
 */
export interface ConfirmActionState<T> {
    /** True while the confirmation modal should be visible. */
    show: Ref<boolean>
    /** The item the user asked to act on, or `null` when no request is active. */
    target: Ref<T | null>
    /** Stores `item` as the pending target and opens the modal. */
    request: (item: T) => void
    /**
     * Runs the configured action against `target.value`, closes the modal on success,
     * and surfaces a generic error message via {@link error} on failure. No-op if no
     * target is set.
     */
    confirm: () => Promise<void>
    /** Localised error message; empty string when no error is shown. */
    error: Ref<string>
}

/**
 * Options for {@link useConfirmAction}.
 */
export interface UseConfirmActionOptions<T> {
    /** Async action invoked with the pending target on confirm. */
    onConfirm: (item: T) => Promise<void>
    /**
     * Optional callback run after a successful action (e.g. to reload a list).
     * Receives the just-acted-on item so callers can inspect it before `target` is reset.
     */
    onSuccess?: (item: T) => void | Promise<void>
    /**
     * Optional external error ref. When provided, the composable writes the localised error
     * message into this ref instead of its own. Useful for views that already maintain a shared
     * `error` ref for the page.
     */
    error?: Ref<string>
}

/**
 * Shared lifecycle for the request-then-confirm modal pattern used in list views: a `target`
 * ref, a `show` flag, plus `request` and `confirm` handlers that share the same error-handling
 * shape. Used for any destructive-ish action (delete, revert, convert, archive, etc.).
 */
export function useConfirmAction<T>(options: UseConfirmActionOptions<T>): ConfirmActionState<T> {
    const {t} = useI18n()
    const show = ref(false)
    const target = ref<T | null>(null) as Ref<T | null>
    const error = options.error ?? ref('')

    function request(item: T) {
        target.value = item
        show.value = true
    }

    async function confirm() {
        const item = target.value
        if (!item) return
        try {
            await options.onConfirm(item)
            show.value = false
            target.value = null
            if (options.onSuccess) await options.onSuccess(item)
        } catch {
            error.value = t('common.error')
        }
    }

    return {show, target, request, confirm, error}
}
