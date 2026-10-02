/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import AppLink from '@/components/navigation/AppLink.vue'

/**
 * The way to a station of another instance: its public page there, opened in the same tab like any
 * other link leaving the site. `rel` marks it external and sends neither an opener nor a referrer
 * along. The station and its instance are appended for screen readers, since every remote card
 * carries a link with the same visible words.
 */
defineProps<{
  href: string
  name: string
  host: string
}>()

const {t} = useI18n()
</script>

<template>
  <AppLink :href="href" :icon="['fas', 'globe']" rel="external noopener noreferrer" class="text-sm">
    {{ t('discovery.visitRemoteStation') }}
    <span class="sr-only">{{ t('discovery.visitRemoteStationTarget', {name, host}) }}</span>
  </AppLink>
</template>
