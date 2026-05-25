/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {EventCategory} from '@/api/types'

defineProps<{
  categories: EventCategory[]
}>()

const search = defineModel<string>('search', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const needsAction = defineModel<boolean>('needsAction', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap gap-3 items-end">
    <div class="flex-1 min-w-48 space-y-1">
      <FieldLabel>{{ t('eventsUpcoming.search') }}</FieldLabel>
      <TextInput v-model="search" :placeholder="t('eventsUpcoming.searchPlaceholder')"/>
    </div>
    <div class="w-48 space-y-1">
      <FieldLabel>{{ t('events.category') }}</FieldLabel>
      <SelectInput v-model="categoryId">
        <option value="">{{ t('eventsUpcoming.allCategories') }}</option>
        <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
      </SelectInput>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('eventsUpcoming.needsAction') }}</FieldLabel>
      <div class="flex items-center px-3 py-2">
        <ToggleInput v-model="needsAction"/>
      </div>
    </div>
  </div>
</template>
