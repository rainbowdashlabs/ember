/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {computed, watch} from 'vue'

export function usePageTitle() {
    if (typeof window === 'undefined') return

    const route = useRoute()
    const {t, te} = useI18n()

    const title = computed(() => {
        const name = route.name as string | undefined
        if (!name) return ''
        const key = `pages.${name}.title`
        return te(key) ? t(key) : ''
    })

    watch(title, (val) => {
        useHead({title: val || undefined})
    }, {immediate: true})
}
