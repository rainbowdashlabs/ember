/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import Alert from '@/components/feedback/Alert.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import ContentRow from '@/components/content/ContentRow.vue'
import SharedLinkShell from './SharedLinkShell.vue'
import {publicContentContext} from '@/util/contentContext'
import {apiUrl} from '@/util/apiUrl'
import type {SharedPage} from '@/api/sharedLinks'

/**
 * A page somebody was sent the link to.
 *
 * <p>Fetched while the server renders rather than after mounting, which every other public page
 * does. A link whose whole purpose is to be pasted into a message has to carry its title and its
 * description in the page the server sends, or the preview drawn beside it says nothing at all.
 */
const {t} = useI18n()
const route = useRoute()

const token = computed(() => String(route.params.token))

const {data: shared, error} = await useAsyncData(
    () => `shared-page-${token.value}`,
    () => $fetch<SharedPage>(`${apiUrl('')}/public/shared/${token.value}`),
    {watch: [token]},
)

const page = computed(() => shared.value?.page ?? null)

/**
 * A page opened to the public since its link was sent moves to its ordinary address, but only where
 * that address answers: the slug path sits behind the station's public pages switch, so sending a
 * reader there blindly would send them from a working link to an error.
 *
 * <p>Through Nuxt's own move rather than the router's, because this runs while the page is rendered
 * on the server and the router cannot answer a request with a redirect there. It rendered and sent
 * the link's own page instead, and only moved once scripts had run, so a reader without them and
 * anything unfurling the link stayed on an address marked not to be indexed.
 */
watch(shared, value => {
  if (value?.ownAddressLive && value.station.publicSlug) {
    void navigateTo(`/public/station/${value.station.publicSlug}/page/${value.path}`, {replace: true})
  }
}, {immediate: true})

useHead(computed(() => {
  const p = page.value
  if (!p) return {meta: [{name: 'robots', content: 'noindex, nofollow'}]}
  const description = p.metaDescription || p.title
  return {
    title: p.title,
    meta: [
      {name: 'robots', content: 'noindex, nofollow'},
      {name: 'referrer', content: 'no-referrer'},
      {name: 'description', content: description},
      {property: 'og:title', content: p.title},
      {property: 'og:description', content: description},
      {property: 'og:type', content: 'website'},
      {name: 'twitter:card', content: 'summary'},
      {name: 'twitter:title', content: p.title},
      {name: 'twitter:description', content: description},
    ],
  }
}))
</script>

<template>
    <SharedLinkShell :brand="shared?.station ?? null">
        <Alert v-if="error || !page" variant="error">{{ t('common.notFound') }}</Alert>
        <ViewContent v-else :title="page.title">
            <div class="space-y-0">
                <ContentRow
                    v-for="row in page.rows"
                    :key="row.id"
                    :row="row"
                    :context="publicContentContext(shared!.station.stationUid, page.title)"/>
            </div>
        </ViewContent>
    </SharedLinkShell>
</template>
