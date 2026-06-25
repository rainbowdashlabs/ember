/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {EventCategory} from '@/api/types'

const {t} = useI18n()

defineProps<{
  categories: EventCategory[]
}>()

const eventCategoryId = defineModel<string>('eventCategoryId', {required: true})
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('events.category') }}</FieldLabel>
    <SelectInput v-model="eventCategoryId">
      <option value="">{{ t('events.noCategory') }}</option>
      <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
    </SelectInput>
  </div>
</template>
