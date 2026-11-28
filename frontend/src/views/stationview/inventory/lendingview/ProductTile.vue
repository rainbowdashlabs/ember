/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AuthImage from '@/components/display/AuthImage.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {ProductSummary} from '@/api/generated/schema'
import MutedText from '@/components/typography/MutedText.vue'
import {offerLook} from '@/util/offerLook'

/** One partner product as a tile of the lending shop: its photo or icon, what it offers and where it is. */
const props = defineProps<{
  summary: ProductSummary
}>()

const {t} = useI18n()

const product = computed(() => props.summary.product)
const look = computed(() => offerLook({
  stationId: props.summary.stationId,
  pictureKey: product.value.pictureKey,
  icon: product.value.icon,
  color: product.value.color,
  homogeneous: product.value.pieces === 0,
}, 256))
const pictureSrc = computed(() => look.value.pictureSrc)
const glyph = computed(() => look.value.glyph)

const offerLine = computed(() => {
  const parts: string[] = []
  const {kinds, pieces} = product.value
  if (kinds > 0) parts.push(kinds === 1 ? t('lendingShop.kindsOne') : t('lendingShop.kinds', {count: kinds}))
  if (pieces > 0) parts.push(pieces === 1 ? t('lendingShop.piecesOne') : t('lendingShop.pieces', {count: pieces}))
  return parts.join(' · ')
})

const distance = computed(() => props.summary.distanceKm == null
    ? t('lendingDistance.distanceUnknown')
    : t('lendingDistance.distanceKm', {distance: props.summary.distanceKm.toFixed(1)}))
</script>

<template>
  <article class="flex h-full flex-col overflow-hidden rounded-theme border border-(--border) bg-(--bg)"
           data-testid="lending-product-tile">
    <div class="flex aspect-[4/3] items-center justify-center bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
      <AuthImage v-if="pictureSrc" :src="pictureSrc" alt="" class="h-full w-full object-cover">
        <template #error><GearGlyph :glyph="glyph" size="lg"/></template>
        <template #loading><GearGlyph :glyph="glyph" size="lg"/></template>
      </AuthImage>
      <GearGlyph v-else :glyph="glyph" size="lg"/>
    </div>
    <div class="flex flex-1 flex-col gap-1 p-3">
      <SubHeader class="leading-tight">{{ product.name }}</SubHeader>
      <MutedText tag="p">
        {{ offerLine }} · <span class="font-medium text-success">{{ t('lendingShop.free', {count: product.free}) }}</span>
      </MutedText>
      <p v-if="product.matched.length > 0" class="text-xs" data-testid="lending-product-matched">
        {{ t('lendingShop.matched', {names: product.matched.join(', ')}) }}
      </p>
      <MutedText v-else-if="product.teaser" tag="p" class="line-clamp-2">{{ product.teaser }}</MutedText>
      <div class="mt-auto flex flex-wrap items-center gap-2 pt-1">
        <StationBadge :station-name="summary.stationName"/>
        <span class="text-xs text-(--text-muted)">{{ distance }}</span>
      </div>
    </div>
  </article>
</template>
