/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, onUnmounted, provide} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {PublicStationInfo} from '@/api/discovery'
import {apiUrl} from '@/util/apiUrl'
import type {PublicPageSummary} from '@/api/publicPages'
import {useCanonical} from '~/composables/useCanonical'
import {usePageHeader} from '@/composables/usePageHeader'
import {useTheme} from '@/composables/useTheme'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const stationUid = computed(() => route.params.stationUid as string)

const logoUrl = computed(() =>
    station.value?.hasLogo ? `/api/v1/public/stations/${station.value.stationUid}/logo?size=256` : null)

const basePath = computed(() => {
  const id = station.value?.publicSlug || stationUid.value
  return `/public/station/${id}`
})

const landingPage = computed(() => {
  if (!station.value?.landingPageSlug) return null
  return publicPages.value.find(p => p.slug === station.value!.landingPageSlug) ?? null
})

const topLevelPages = computed(() => {
  const landingSlug = station.value?.landingPageSlug
  return publicPages.value.filter(p => p.parentId == null && p.slug !== landingSlug)
})

function childrenOf(parentId: number) {
  return publicPages.value.filter(p => p.parentId === parentId)
}

/**
 * Fetched during the server render rather than after mount. Every public page of a station hangs
 * inside this shell, so loading it in the browser left a crawler, a link preview and a reader
 * without JavaScript with an empty frame where the station's blog, wiki and calendar should be.
 */
// Resolved here rather than inside the loader: the address comes from the runtime configuration,
// and reading that needs the Nuxt instance, which a loader running on its own no longer has.
const apiBase = apiUrl('')

const {data: shell, error: loadError, status} = await useAsyncData(
    `public-station-${route.params.stationUid}`,
    async () => {
      const info = await $fetch<PublicStationInfo>(`${apiBase}/public/station/${stationUid.value}/info`)
      const pages = info.hasPublicPages
          ? await $fetch<PublicPageSummary[]>(`${apiBase}/public/pages/${info.stationUid}`)
          : []
      return {info, pages}
    },
    {watch: [stationUid]},
)

const station = computed(() => shell.value?.info ?? null)
const publicPages = computed(() => shell.value?.pages ?? [])
const loading = computed(() => status.value === 'pending')
const error = computed(() => (loadError.value ? t('common.error') : ''))

onMounted(() => {
  if (!station.value) return

  const {applyStationOverride} = useTheme()
  applyStationOverride(station.value.defaultTheme, station.value.defaultFeel, station.value.customThemeColors)

  // A station reached by its identifier moves to its readable address once the page is up.
  if (station.value.publicSlug && UUID_REGEX.test(stationUid.value)) {
    router.replace(route.path.replace(
        `/public/station/${stationUid.value}`,
        `/public/station/${station.value.publicSlug}`,
    ))
  }
})

onUnmounted(() => {
  useTheme().clearStationOverride()
})

provide('publicStation', station)

useCanonical(() => route.path)

useHead(computed(() => {
  if (!station.value) return {}
  const s = station.value
  const desc = s.description || `Öffentliche Seite von ${s.name}`
  const stationLogo = s.hasLogo ? `/api/v1/public/stations/${s.stationUid}/logo?size=512` : undefined
  return {
    title: s.name,
    meta: [
      {name: 'description', content: desc},
      {property: 'og:title', content: `${s.name} - Ember`},
      {property: 'og:description', content: desc},
      {property: 'og:type', content: 'website'},
      ...(stationLogo ? [
        {property: 'og:image', content: stationLogo},
        {name: 'twitter:image', content: stationLogo},
      ] : []),
      {name: 'twitter:card', content: stationLogo ? 'summary_large_image' : 'summary'},
      {name: 'twitter:title', content: `${s.name} - Ember`},
      {name: 'twitter:description', content: desc},
    ],
    script: [
      {
        type: 'application/ld+json',
        innerHTML: JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'Organization',
          name: s.name,
          ...(s.description ? {description: s.description} : {}),
          ...(stationLogo ? {logo: stationLogo} : {}),
        }),
      },
    ],
  }
}))
</script>

<template>
  <Spinner v-if="loading" size="lg" class="mt-16"/>
  <Alert v-else-if="error" variant="error" class="m-8">{{ error }}</Alert>

  <SidebarLayout
      v-else-if="station"
      :station-name="station.name"
      :station-logo-url="logoUrl"
      :title="pageTitle"
      :subtitle="pageSubtitle"
  >
    <template #sidebar="{ close }">
      <!-- Landing page (home) first -->
      <SidebarLink v-if="landingPage"
                   :icon="['fas', 'house']"
                   :name="'public-page-' + landingPage.slug"
                   :to="basePath + '/page/' + landingPage.path"
                   @navigate="close">
        {{ landingPage.title }}
      </SidebarLink>
      <!-- Blog -->
      <SidebarLink v-if="station.hasPublicBlog" :icon="['fas', 'newspaper']" name="public-blog" :to="basePath + '/blog'" @navigate="close">
        {{ t('publicStation.blog') }}
      </SidebarLink>
      <!-- Calendar -->
      <SidebarLink v-if="station.hasPublicCalendar" :icon="['fas', 'calendar-days']" name="public-station-calendar" :to="basePath + '/calendar'" @navigate="close">
        {{ t('publicStation.calendar') }}
      </SidebarLink>
      <!-- Knowledge base -->
      <SidebarLink v-if="station.hasPublicKb" :icon="['fas', 'book-open']" name="public-kb" :to="basePath + '/knowledge'" @navigate="close">
        {{ t('publicStation.knowledgeBase') }}
      </SidebarLink>
      <!-- Waitlist registration -->
      <SidebarLink v-if="station.hasPublicWaitlist" :icon="['fas', 'clipboard-list']" name="public-waitlist" :to="basePath + '/waitlist'" @navigate="close">
        {{ t('publicStation.waitlist') }}
      </SidebarLink>
      <!-- Remaining pages (excluding landing page), with nested children -->
      <template v-if="station.hasPublicPages">
        <template v-for="page in topLevelPages" :key="page.id">
          <!-- Page with children → SidebarGroup -->
          <SidebarGroup v-if="childrenOf(page.id).length > 0"
                        :icon="['fas', 'file-lines']"
                        :label="page.title"
                        :prefix="basePath + '/page/' + page.path"
                        :to="basePath + '/page/' + page.path"
                        :name="'public-page-' + page.slug"
                        @navigate="close">
            <SidebarLink v-for="child in childrenOf(page.id)" :key="child.id"
                         :icon="['fas', 'file-lines']"
                         :name="'public-page-' + child.slug"
                         :to="basePath + '/page/' + child.path"
                         @navigate="close">
              {{ child.title }}
            </SidebarLink>
          </SidebarGroup>
          <!-- Page without children → SidebarLink -->
          <SidebarLink v-else
                       :icon="['fas', 'file-lines']"
                       :name="'public-page-' + page.slug"
                       :to="basePath + '/page/' + page.path"
                       @navigate="close">
            {{ page.title }}
          </SidebarLink>
        </template>
      </template>
    </template>

    <template #header>
      <router-link to="/discovery">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'compass']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('publicStation.backToDiscovery') }}</span>
        </SecondaryButton>
      </router-link>
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
