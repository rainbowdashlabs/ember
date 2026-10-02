/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import DiscoveryLink from '@/components/discovery/DiscoveryLink.vue'
import {offerLinks} from '@/components/discovery/tileRules'
import {isRemoteEntry} from '@/api/discovery'
import type {DiscoveryEntry} from '@/api/generated/schema'

/**
 * The public parts of a station as chips, each leading straight to that part of its public page. The
 * station's name is appended for screen readers, since every tile carries chips with the same words.
 */
const props = defineProps<{
  station: DiscoveryEntry
}>()

const {t} = useI18n()

const links = computed(() => offerLinks(props.station))
</script>

<template>
  <ul v-if="links.length > 0" class="flex flex-wrap gap-1.5">
    <li v-for="link in links" :key="link.key">
      <DiscoveryLink :href="link.href" :external="isRemoteEntry(station)">
        <SecondaryBadge>
          <AppIcon :icon="link.icon" aria-hidden="true" class="mr-1"/>
          {{ t(`discovery.offer.${link.key}`) }}
        </SecondaryBadge>
        <span class="sr-only">{{ t('discovery.offerOf', {name: station.name}) }}</span>
      </DiscoveryLink>
    </li>
  </ul>
</template>
