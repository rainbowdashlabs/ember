/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {TrackingStatusName} from '@/api/dataTracking'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'

/**
 * Editor for the shared "status select + reason/rationale" block on each
 * tracking context section (transfer, export, deletion).
 *
 * The component owns no state; it forwards updates via v-model:status,
 * v-model:reason, and v-model:rationale so callers stay typed and binding-free.
 */
const status = defineModel<TrackingStatusName>('status', {required: true})
const reason = defineModel<string | null | undefined>('reason')
const rationale = defineModel<string | null | undefined>('rationale')

defineProps<{
  statuses: TrackingStatusName[]
  showRationaleOnTracked?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <SelectInput
      :model-value="status"
      @update:model-value="status = $event as TrackingStatusName"
  >
    <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
  </SelectInput>
  <TextInput
      v-if="status === 'IGNORED'"
      :model-value="reason ?? ''"
      :placeholder="t('adminDataTracking.detail.reason')"
      @update:model-value="reason = $event"
  />
  <TextAreaInput
      v-if="showRationaleOnTracked && status === 'TRACKED'"
      :model-value="rationale ?? ''"
      :placeholder="t('adminDataTracking.detail.rationale')"
      :rows="2"
      @update:model-value="rationale = $event"
  />
  <TextAreaInput
      v-if="status === 'UNVERIFIED'"
      :model-value="(showRationaleOnTracked ? rationale : reason) ?? ''"
      :placeholder="t('adminDataTracking.detail.reviewNote')"
      :rows="2"
      @update:model-value="showRationaleOnTracked ? rationale = $event : reason = $event"
  />
</template>
