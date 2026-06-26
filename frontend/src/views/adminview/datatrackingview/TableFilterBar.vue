/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'

type FilterContext = 'all' | 'transfer' | 'gdprExport' | 'gdprDeletion'
type FilterStatus = 'all' | 'TRACKED' | 'IGNORED' | 'UNVERIFIED' | 'NEEDS_REVIEW'

const search = defineModel<string>('search', {required: true})
const filterContext = defineModel<FilterContext>('filterContext', {required: true})
const filterStatus = defineModel<FilterStatus>('filterStatus', {required: true})

const {t} = useI18n()

const contextOptions: FilterContext[] = ['all', 'transfer', 'gdprExport', 'gdprDeletion']
const statusOptions: FilterStatus[] = ['all', 'TRACKED', 'IGNORED', 'UNVERIFIED', 'NEEDS_REVIEW']
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 mb-3">
    <SearchInput
        v-model="search"
        :placeholder="t('adminDataTracking.searchPlaceholder')"
        class="max-w-xs"
    />
    <SelectionToggleButton
        v-for="opt in contextOptions"
        :key="opt"
        :selected="filterContext === opt"
        @toggle="filterContext = opt"
    >
      {{ t(`adminDataTracking.context.${opt}`) }}
    </SelectionToggleButton>
    <span class="mx-2 text-(--text-muted)">|</span>
    <SelectionToggleButton
        v-for="opt in statusOptions"
        :key="opt"
        :selected="filterStatus === opt"
        @toggle="filterStatus = opt"
    >
      {{ t(`adminDataTracking.filter.${opt}`) }}
    </SelectionToggleButton>
  </div>
</template>
