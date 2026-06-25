/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {dismissToast, getToasts, showToast, type Toast} from '@/util/toast'

export type {Toast}

export function useToast() {
    return {
        toasts: getToasts(),
        show: showToast,
        dismiss: dismissToast,
    }
}
