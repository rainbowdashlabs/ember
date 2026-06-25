/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import CompactToggle from '@/components/input/toggle/CompactToggle.vue'
import {type ProfileField, parseFieldConfig} from '@/api/types'

const props = defineProps<{
  field: ProfileField
}>()

const emit = defineEmits<{
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
}>()

function onReadonlyChange(v: boolean) {
  emit('toggleConfig', props.field, 'readonly', v)
  if (v) emit('toggleConfig', props.field, 'required', false)
}
</script>

<template>
  <div class="flex justify-center">
    <CompactToggle :model-value="!!parseFieldConfig(field.config).required" :disabled="!!parseFieldConfig(field.config).readonly"
                 @update:model-value="v => emit('toggleConfig', field, 'required', v)"/>
  </div>
  <div class="flex justify-center">
    <CompactToggle :model-value="!!parseFieldConfig(field.config).readonly"
                 @update:model-value="onReadonlyChange"/>
  </div>
  <div class="flex justify-center">
    <CompactToggle :model-value="!!parseFieldConfig(field.config).notifyOnChange"
                 @update:model-value="v => emit('toggleConfig', field, 'notifyOnChange', v)"/>
  </div>
  <div class="flex justify-center">
    <CompactToggle :model-value="!!parseFieldConfig(field.config).overview"
                 @update:model-value="v => emit('toggleConfig', field, 'overview', v)"/>
  </div>
  <div class="flex justify-center">
    <CompactToggle :model-value="!!field.keepOnArchive"
                 @update:model-value="v => emit('toggleKeepOnArchive', field, v)"/>
  </div>
</template>
