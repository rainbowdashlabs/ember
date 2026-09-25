/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {shiftIsHeld} from '@/util/modifierKeys'
import {describeFailure, type Failure} from '@/util/failure'

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
    /**
     * Stores `item` as the pending target and opens the modal, or carries the action out at once
     * while shift is held.
     */
    request: (item: T) => void
    /**
     * Runs the configured action against `target.value`, closes the modal on success,
     * and surfaces a generic error message via {@link error} on failure. No-op if no
     * target is set.
     */
    confirm: () => Promise<void>
    /** Localised error message; empty string when no error is shown. */
    error: Ref<string>
    /**
     * The same failure described: what sort it was, what to do about it, and whether it is a fault
     * worth reporting. It also tells the two apart: the action failing and the screen failing to
     * catch up afterwards are not the same news.
     */
    failure: Ref<Failure | null>
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
    /**
     * Optional external failure ref, the counterpart of {@link error}. A page funnelling its
     * failures into one alert has to pass both, or the sentence arrives there while the guidance
     * and the offer to report it stay behind in this composable's own ref.
     */
    failure?: Ref<Failure | null>
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
    const failure = options.failure ?? ref<Failure | null>(null)

    /**
     * Shift skips the question, everywhere one is asked.
     *
     * <p>Somebody clearing out twenty rows knows what the dialog is going to say by the third one,
     * and answering it nineteen more times teaches them to answer without reading. Holding shift
     * says they have read it, and the action happens on the spot.
     */
    function request(item: T) {
        target.value = item
        if (shiftIsHeld()) {
            void confirm()
            return
        }
        show.value = true
    }

    /**
     * Carries the action out, then tells whoever asked to catch up.
     *
     * <p>The two are answered for separately, which is not tidiness. They used to share one `try`,
     * and `onSuccess` is the list reloading almost everywhere it is passed, so a deletion that
     * worked and a list that then failed to refresh said the same sentence. A reader told that
     * deleting did not work does it again, on a thing that is already gone.
     */
    async function confirm() {
        const item = target.value
        if (!item) return
        try {
            await options.onConfirm(item)
            show.value = false
            target.value = null
        } catch (e) {
            record(e)
            return
        }

        if (!options.onSuccess) return
        try {
            await options.onSuccess(item)
        } catch (e) {
            record(e, t('failure.staleAfterAction'))
        }
    }

    /** Records what went wrong, with a sentence of its own where the caller has a better one. */
    function record(e: unknown, message?: string) {
        const described = describeFailure(e, t)
        failure.value = message ? {...described, message} : described
        error.value = failure.value.message
    }

    return {show, target, request, confirm, error, failure}
}
