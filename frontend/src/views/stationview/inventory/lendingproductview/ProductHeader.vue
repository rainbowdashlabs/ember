/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PieceThumb from '@/components/inventory/PieceThumb.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import type {ProductDetail} from '@/api/generated/schema'
import {offerLook} from '@/util/offerLook'
import ProductDescription from './ProductDescription.vue'

/**
 * The top of a product page: photo, station, distance, where the gear is kept, description. The
 * name is the page title above it.
 */
const props = defineProps<{
  detail: ProductDetail
}>()

const {t} = useI18n()

const product = computed(() => props.detail.product)
const look = computed(() => offerLook({
  stationId: props.detail.stationId,
  pictureKey: product.value.pictureKey,
  icon: product.value.icon,
  color: product.value.color,
  homogeneous: product.value.pieces.length === 0,
}, 512))

const address = computed(() => {
  const place = product.value.address
  if (!place) return null
  return [place.addressLine, [place.postalCode, place.city].filter(Boolean).join(' ')].filter(Boolean).join(', ')
})
const mapHref = computed(() => address.value
    ? `https://www.openstreetmap.org/search?query=${encodeURIComponent(address.value)}`
    : null)

const distance = computed(() => props.detail.distanceKm == null
    ? t('lendingDistance.distanceUnknown')
    : t('lendingDistance.distanceKm', {distance: props.detail.distanceKm.toFixed(1)}))
</script>

<template>
  <header class="flex flex-col gap-4 sm:flex-row">
    <div class="flex aspect-[4/3] w-full shrink-0 items-center justify-center overflow-hidden rounded-theme
                bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 sm:w-72">
      <PieceThumb v-bind="look" fit="cover"/>
    </div>
    <div class="min-w-0 flex-1 space-y-2">
      <div class="flex flex-wrap items-center gap-2">
        <StationBadge :station-name="detail.stationName"/>
        <MutedText>{{ distance }}</MutedText>
      </div>
      <MutedText v-if="address" tag="p" data-testid="lending-product-address">
        {{ address }} ·
        <AppLink v-if="mapHref" :href="mapHref" external :icon="['fas', 'map-location-dot']">{{ t('lendingProduct.openMap') }}</AppLink>
      </MutedText>
      <ProductDescription :text="product.description"/>
    </div>
  </header>
</template>
