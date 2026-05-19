/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

const needsReconsent = ref(false)

export function useConsentGuard() {
    return {
        needsReconsent,
        setNeedsReconsent(value: boolean) {
            needsReconsent.value = value
        },
    }
}
