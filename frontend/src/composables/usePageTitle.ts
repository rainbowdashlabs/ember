/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {usePageHeader} from '@/composables/usePageHeader'
import {pageModuleKeys, titleWithModule} from '@/util/pageModule'

/**
 * Mirrors the shared page header state into the browser tab title. The header
 * itself is populated by whichever {@code ViewContent} instance is currently
 * mounted (via its {@code title} prop). Registered once as a reactive head
 * binding - repeated {@code useHead} calls from a watcher would stack head
 * entries and keep the previous title alive when the header becomes empty.
 *
 * <p>The tab reads the page, then the parts of the product it belongs to, then the product: a ticket
 * is "GEM-4 Löschzug melden - Boards - Ember", and an association's appointment is
 * "Sommerfest - Termine - Verband - Ember", which is what tells it apart from the station's own
 * appointment page drawn by the very same view. A dozen tabs on one installation are otherwise a
 * row of names with nothing saying which is a ticket and which is a member, and the same string is
 * what a bookmark and a history entry keep. The last part comes from the template in `app.vue`.
 */
export function usePageTitle() {
    if (typeof window === 'undefined') return

    const {title} = usePageHeader()
    const {t} = useI18n()
    const route = useRoute()

    useHead({
        title: () => titleWithModule(title.value, pageModuleKeys(route.path).map(key => t(key))) || null,
    })
}
