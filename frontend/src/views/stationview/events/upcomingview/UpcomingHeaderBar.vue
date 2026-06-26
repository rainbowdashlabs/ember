/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

type ViewMode = 'list' | 'calendar'

const viewMode = defineModel<ViewMode>('viewMode', {required: true})

defineProps<{
  canCreate: boolean
}>()

const emit = defineEmits<{
  create: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <div>
      <PrimaryButton v-if="canCreate" :icon="['fas', 'plus']" @click="emit('create')">
        {{ t('events.addEvent') }}
      </PrimaryButton>
    </div>
    <div class="flex gap-2">
      <SelectionToggleButton :selected="viewMode === 'list'" @toggle="viewMode = 'list'">
        <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
        {{ t('eventsUpcoming.viewList') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="viewMode === 'calendar'" @toggle="viewMode = 'calendar'">
        <font-awesome-icon :icon="['fas', 'calendar-days']" class="mr-1"/>
        {{ t('eventsUpcoming.viewCalendar') }}
      </SelectionToggleButton>
    </div>
  </div>
</template>
