/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, watch} from 'vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useI18n} from 'vue-i18n'
import {useTheme} from '@/composables/useTheme'
import type {SharedBrand} from '@/api/sharedLinks'

/**
 * The wrapper around something somebody was sent a link to.
 *
 * <p>A form or a page sent to somebody is not a doorway into the station that sent it, so this is
 * not the public station shell: no menu, no calendar, no wiki, no blog, nothing to click into. The
 * station's name and colours are here so the reader knows who is asking, and the footer is here
 * because a page that collects strangers' answers has to reach an imprint.
 *
 * <p>It also asks the station nothing. The shell fetches a station's public information and shows an
 * error in place of its content when that answers nothing, which it does for a station with no
 * public site: exactly the station most likely to send somebody a form.
 */
const props = defineProps<{
  brand: SharedBrand | null
}>()

const {t} = useI18n()

const logoUrl = computed(() =>
    props.brand?.hasLogo ? `/api/v1/public/stations/${props.brand.stationUid}/logo?size=128` : null)

/**
 * The station's colours, once the page is up.
 *
 * <p>Only in the browser: applying a theme writes into the document, which does not exist while the
 * server renders. What the server sends is already in the station's colours, because the plugin that
 * picks a theme reads this link before anything is drawn.
 */
function paintStation(brand: SharedBrand | null) {
  if (!brand || import.meta.server) return
  const {applyStationOverride} = useTheme()
  applyStationOverride(brand.defaultTheme, brand.defaultFeel, brand.customThemeColors)
}

onMounted(() => paintStation(props.brand))
watch(() => props.brand, paintStation)
</script>

<template>
    <div class="flex min-h-screen flex-col">
        <header class="border-b border-(--border) bg-(--bg)">
            <div class="mx-auto flex max-w-3xl items-center gap-3 px-4 py-4">
                <img v-if="logoUrl" :src="logoUrl" :alt="brand?.name" class="h-10 w-10 rounded object-contain"/>
                <div class="min-w-0">
                    <p v-if="brand" class="truncate text-lg font-semibold">{{ brand.name }}</p>
                    <MutedText tag="p" size="sm">{{ t('shareLink.sentYou') }}</MutedText>
                </div>
            </div>
        </header>

        <main class="flex-1">
            <div class="mx-auto max-w-3xl px-4 py-8">
                <slot/>
            </div>
        </main>

        <AppFooter/>
    </div>
</template>
