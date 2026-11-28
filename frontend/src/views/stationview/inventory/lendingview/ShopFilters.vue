/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type {ShopSort} from './useShopQuery'

/**
 * How the shop is narrowed: the days gear is wanted, a search, one partner station or only those the
 * cart already asks, and the order.
 */
const search = defineModel<string>('search', {required: true})
const dateFrom = defineModel<string>('dateFrom', {required: true})
const dateTo = defineModel<string>('dateTo', {required: true})
const station = defineModel<string>('station', {required: true})
const sort = defineModel<ShopSort>('sort', {required: true})
const onlyInCart = defineModel<boolean>('onlyInCart', {required: true})

defineProps<{
  stations: {id: string; name: string}[]
}>()

const {t} = useI18n()
const fromId = useId()
const toId = useId()
const searchId = useId()
const stationId = useId()
const sortId = useId()
const inCartId = useId()
</script>

<template>
  <div class="space-y-3">
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div class="space-y-1">
        <FieldLabel :for="fromId">{{ t('lending.dateFrom') }}</FieldLabel>
        <DateInput :id="fromId" v-model="dateFrom" data-testid="lending-shop-from"/>
      </div>
      <div class="space-y-1">
        <FieldLabel :for="toId">{{ t('lending.dateTo') }}</FieldLabel>
        <DateInput :id="toId" v-model="dateTo" :min="dateFrom || undefined" data-testid="lending-shop-to"/>
      </div>
      <div class="space-y-1 sm:col-span-2">
        <FieldLabel :id="searchId">{{ t('lendingShop.search') }}</FieldLabel>
        <SearchInput v-model="search" :aria-labelledby="searchId" :placeholder="t('lendingShop.searchPlaceholder')"
                     data-testid="lending-shop-search"/>
      </div>
    </div>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div class="space-y-1">
        <FieldLabel :for="stationId">{{ t('lendingShop.station') }}</FieldLabel>
        <SelectInput :id="stationId" v-model="station" class="w-full" data-testid="lending-shop-station">
          <option value="">{{ t('lendingShop.allStations') }}</option>
          <option v-for="option in stations" :key="option.id" :value="option.id">{{ option.name }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel :for="sortId">{{ t('lendingShop.sort') }}</FieldLabel>
        <SelectInput :id="sortId" v-model="sort" class="w-full" data-testid="lending-shop-sort">
          <option value="best">{{ search.trim() ? t('lendingShop.sortBest') : t('lendingShop.sortNearest') }}</option>
          <option value="name">{{ t('lendingShop.sortName') }}</option>
        </SelectInput>
      </div>
      <div class="flex items-center gap-2 sm:col-span-2 sm:self-end sm:pb-2">
        <CheckboxInput :id="inCartId" v-model="onlyInCart" data-testid="lending-shop-in-cart"/>
        <FieldLabel :for="inCartId">{{ t('lendingShop.onlyInCart') }}</FieldLabel>
      </div>
    </div>
  </div>
</template>
