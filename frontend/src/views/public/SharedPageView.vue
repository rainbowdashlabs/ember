/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import ContentRow from '@/components/content/ContentRow.vue'
import SharedLinkShell from './SharedLinkShell.vue'
import {usePublicFailure} from '@/composables/usePublicFailure'
import {publicContentContext} from '@/util/contentContext'
import {apiUrl} from '@/util/apiUrl'
import {socialMeta, stationLogoImage, useAbsoluteUrl} from '@/util/socialMeta'
import {publicPageImageUrl} from '@/api/publicPages'
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
 * A fetch that succeeded and still left no page behind counts as the link being gone: from where the
 * reader stands there is nothing to tell the two apart, and both are answered by asking whoever sent
 * the link for a current one.
 */
const linkFailure = usePublicFailure(
    computed(() => error.value ?? (page.value ? null : {response: {status: 404}})),
    {message: 'shareLink.gone', guidance: 'shareLink.goneGuidance'},
)

const absoluteUrl = useAbsoluteUrl()

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
  const station = shared.value?.station
  const image = absoluteUrl(p.ogImageHash && station
      ? publicPageImageUrl(station.stationUid, p.ogImageHash)
      : stationLogoImage(station))
  return {
    title: p.title,
    meta: [
      {name: 'robots', content: 'noindex, nofollow'},
      {name: 'referrer', content: 'no-referrer'},
      ...socialMeta({title: p.title, description, imageUrl: image}),
    ],
  }
}))
</script>

<template>
    <SharedLinkShell :brand="shared?.station ?? null">
        <FailureAlert v-if="linkFailure" :failure="linkFailure"/>
        <ViewContent v-else-if="page" :title="page.title">
            <div class="space-y-0">
                <ContentRow
                    v-for="row in page.rows"
                    :key="row.id"
                    :row="row"
                    :context="publicContentContext(shared!.station.stationUid, page.title, shared!.station.timezone)"/>
            </div>
        </ViewContent>
    </SharedLinkShell>
</template>
