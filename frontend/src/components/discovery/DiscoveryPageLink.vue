/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import DiscoveryLink from '@/components/discovery/DiscoveryLink.vue'
import {isRemoteEntry} from '@/api/discovery'
import type {DiscoveryEntry} from '@/api/generated/schema'

/**
 * The way to a station's public page, on this instance or on its own. The station, and the instance of a
 * remote one, are appended for screen readers, since every tile carries a link with the same visible words.
 */
defineProps<{
  station: DiscoveryEntry
  href: string
}>()

const {t} = useI18n()
</script>

<template>
  <DiscoveryLink :href="href" :external="isRemoteEntry(station)">
    <AppIcon :icon="['fas', 'globe']" aria-hidden="true"/>
    {{ t('discovery.visitStation') }}
    <span v-if="station.instanceHost" class="sr-only">{{ t('discovery.visitStationOn', {name: station.name, host: station.instanceHost}) }}</span>
    <span v-else class="sr-only">{{ station.name }}</span>
  </DiscoveryLink>
</template>
