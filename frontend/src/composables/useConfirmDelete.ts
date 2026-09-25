/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import {useConfirmAction, type ConfirmActionState} from './useConfirmAction'
import type {Failure} from '@/util/failure'

/**
 * Delete-specific shape kept for back-compat with existing call sites that bind
 * `requestDelete` to their list-row trigger.
 */
export interface ConfirmDeleteState<T> {
    show: Ref<boolean>
    target: Ref<T | null>
    requestDelete: (item: T) => void
    confirm: () => Promise<void>
    error: Ref<string>
    /** The same failure described, as {@link useConfirmAction} gives it. */
    failure: Ref<Failure | null>
}

export interface UseConfirmDeleteOptions<T> {
    onDelete: (item: T) => Promise<void>
    onSuccess?: (item: T) => void | Promise<void>
    error?: Ref<string>
    /** The counterpart of {@link error}, for a page funnelling its failures into one alert. */
    failure?: Ref<Failure | null>
}

/**
 * Thin adapter over {@link useConfirmAction} that names the trigger `requestDelete` and the
 * action `onDelete`. New call sites that aren't delete-specific should reach for
 * {@link useConfirmAction} directly.
 */
export function useConfirmDelete<T>(options: UseConfirmDeleteOptions<T>): ConfirmDeleteState<T> {
    const state: ConfirmActionState<T> = useConfirmAction<T>({
        onConfirm: options.onDelete,
        onSuccess: options.onSuccess,
        error: options.error,
        failure: options.failure,
    })
    return {
        show: state.show,
        target: state.target,
        requestDelete: state.request,
        confirm: state.confirm,
        error: state.error,
        failure: state.failure,
    }
}
