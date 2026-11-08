/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {ACCOUNT_NAV} from '@/data/sidebar/accountNav'
import {resolveNav} from '@/util/navTree'

/** The account sidebar, translated. */
export function useAccountNav() {
    const {t} = useI18n()

    const nodes = computed(() => resolveNav(ACCOUNT_NAV, null, t))

    return {nodes}
}
