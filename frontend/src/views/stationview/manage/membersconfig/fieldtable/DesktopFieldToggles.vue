/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import CompactToggle from '@/components/input/toggle/CompactToggle.vue'
import WritabilitySelect from './WritabilitySelect.vue'
import {type ProfileField, parseFieldConfig} from '@/api/profileFields'
import {holdsAnswer} from '@/components/profilefields/fieldLayout'
import {useFieldsCapabilities, type WritabilityName} from '@/composables/useFieldsConfig'

/**
 * What the question itself says, which holds for everybody asked it.
 *
 * <p>Who may write the answer is one of them. An association has a third party to lock out as well, so
 * where there is one it gets the named choice in place of the switch.
 */
defineProps<{
  field: ProfileField
}>()

const emit = defineEmits<{
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
  toggleRequired: [field: ProfileField, value: boolean]
  toggleReadonly: [field: ProfileField, value: boolean]
  setWritability: [field: ProfileField, level: WritabilityName]
}>()

const capabilities = useFieldsCapabilities()
</script>

<template>
  <template v-if="!holdsAnswer(field)">
    <div v-for="column in 5" :key="column"/>
  </template>
  <template v-else>
    <div class="flex justify-center">
      <CompactToggle :model-value="!!field.required"
                     @update:model-value="v => emit('toggleRequired', field, v)"/>
    </div>
    <div class="flex justify-center">
      <WritabilitySelect v-if="capabilities.writability" :field="field"
                         @set="(f, level) => emit('setWritability', f, level)"/>
      <CompactToggle v-else :model-value="!!field.readonly"
                     @update:model-value="v => emit('toggleReadonly', field, v)"/>
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
</template>
