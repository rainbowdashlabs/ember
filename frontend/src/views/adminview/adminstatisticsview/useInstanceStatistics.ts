/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import client from '@/api/client'
import {type AdminStatistics, StatisticsWindow} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/** The anchor of the federation section, which the overview links to. */
export const FEDERATION_ANCHOR = 'federation'

/**
 * The instance statistics over the window the reader chose.
 *
 * <p>Switching the window quickly can leave an older request answering after a newer one. The
 * answer names its window, so one that no longer matches the choice is dropped rather than shown
 * under the wrong selection, and the page counts as loading until the last request is back. When
 * the page is opened at `#federation`, it scrolls there once the first figures are drawn, since the
 * section does not exist before they arrive.
 */
export function useInstanceStatistics() {
    const {t} = useI18n()
    const route = useRoute()
    const statisticsWindow = ref<StatisticsWindow>(StatisticsWindow.DAYS_30)
    const stats = ref<AdminStatistics | null>(null)
    const failure = ref<Failure | null>(null)
    const open = ref(0)
    const loading = computed(() => open.value > 0)
    let scrolled = false

    async function scrollToAnchor() {
        if (scrolled || route.hash !== `#${FEDERATION_ANCHOR}`) return
        scrolled = true
        await nextTick()
        document.getElementById(FEDERATION_ANCHOR)?.scrollIntoView({block: 'start'})
    }

    async function load() {
        const asked = statisticsWindow.value
        open.value++
        try {
            const answer = (await client.get<AdminStatistics>('/admin/statistics', {params: {window: asked}})).data
            if (answer.window !== statisticsWindow.value) return
            stats.value = answer
            failure.value = null
            await scrollToAnchor()
        } catch (e) {
            if (asked === statisticsWindow.value) failure.value = describeFailure(e, t)
        } finally {
            open.value--
        }
    }

    onMounted(load)
    watch(statisticsWindow, load)

    return {statisticsWindow, stats, loading, failure}
}
