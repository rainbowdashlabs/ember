/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {followModifierKeys} from '@/util/modifierKeys'

/** Follows the shift key from the moment the app is up in the browser, for every page alike. */
export default defineNuxtPlugin(() => {
    followModifierKeys()
})
