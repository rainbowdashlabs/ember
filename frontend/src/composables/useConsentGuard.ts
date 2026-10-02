/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'

/** Whether the reader has to agree to changed terms again before they may go anywhere else. */
export function useConsentGuard() {
    const needsReconsent = useState('useConsentGuard', () => false)
    return {
        needsReconsent: readonly(needsReconsent),
        setNeedsReconsent(value: boolean) {
            needsReconsent.value = value
        },
    }
}
