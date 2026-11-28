/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'
import SubHeader from '@/components/typography/SubHeader.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'

/** How much the station offers and how much of it partners see without a text or photo, leading into the catalog. */
defineProps<{
  summary: {offered: number; noDescription: number; noPicture: number}
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()

function link(filter: string) {
  return routes.lendingOffer ? {name: routes.lendingOffer, query: {filter}} : ''
}
</script>

<template>
  <section class="space-y-1" data-testid="lending-overview-offer">
    <SubHeader>{{ t('lendingOffer.summaryTitle') }}</SubHeader>
    <ul class="space-y-1 text-sm">
      <li><RouterLink :to="link('offered')" class="hover:underline">{{ t('lendingOffer.summaryOffered', {count: summary.offered}) }}</RouterLink></li>
      <li v-if="summary.noDescription > 0">
        <RouterLink :to="link('noDescription')" class="text-primary hover:underline">{{ t('lendingOffer.summaryNoDescription', {count: summary.noDescription}) }}</RouterLink>
      </li>
      <li v-if="summary.noPicture > 0">
        <RouterLink :to="link('noPicture')" class="text-primary hover:underline">{{ t('lendingOffer.summaryNoPicture', {count: summary.noPicture}) }}</RouterLink>
      </li>
    </ul>
  </section>
</template>
