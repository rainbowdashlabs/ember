/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

/** The search over the offers and the period the free pieces are counted against. */
const searchQuery = defineModel<string>('searchQuery', {required: true})
const dateFrom = defineModel<string>('dateFrom', {required: true})
const dateTo = defineModel<string>('dateTo', {required: true})

const {t} = useI18n()
</script>

<template>
  <div>
    <SearchInput
        v-model="searchQuery"
        :placeholder="t('lending.searchInventory')"
        class="mb-4"
    />

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <FieldLabel class="mb-1">{{ t('lending.dateFrom') }}</FieldLabel>
        <DateInput v-model="dateFrom"/>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('lending.dateTo') }}</FieldLabel>
        <DateInput v-model="dateTo"/>
      </div>
    </div>
  </div>
</template>
