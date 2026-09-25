/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventDateRangeFields from './EventDateRangeFields.vue'
import type {EventCategory} from '@/api/events'

/**
 * What a list of appointments is narrowed to, on whichever screen holds one.
 *
 * <p>One bar for both screens, because two would drift apart the day either was touched. A screen
 * with a narrowing of its own puts it in the slot at the end rather than growing a second bar.
 */
defineProps<{
  categories: EventCategory[]
}>()

const search = defineModel<string>('search', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const from = defineModel<string>('from', {required: true})
const to = defineModel<string>('to', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap gap-3 items-end">
    <div class="flex-1 min-w-48 space-y-1">
      <FieldLabel>{{ t('eventsUpcoming.search') }}</FieldLabel>
      <SearchInput v-model="search" :placeholder="t('eventsUpcoming.searchPlaceholder')"/>
    </div>
    <div class="w-48 space-y-1">
      <FieldLabel>{{ t('events.category') }}</FieldLabel>
      <SelectInput v-model="categoryId">
        <option value="">{{ t('eventsUpcoming.allCategories') }}</option>
        <option v-for="category in categories" :key="category.id" :value="String(category.id)">{{ category.name }}</option>
      </SelectInput>
    </div>
    <EventDateRangeFields v-model:from="from" v-model:to="to"/>
    <slot/>
  </div>
</template>
