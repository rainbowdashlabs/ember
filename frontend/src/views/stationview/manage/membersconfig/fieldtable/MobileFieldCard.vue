/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CompactToggle from '@/components/input/toggle/CompactToggle.vue'
import {type ProfileField, parseFieldConfig} from '@/api/profileFields'
import {isSection, widthOf} from '@/components/profilefields/fieldLayout'
import {widthLabel} from '../fieldTypes'

const {t} = useI18n()

const props = defineProps<{
  field: ProfileField
  typeLabel: string
}>()

const emit = defineEmits<{
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
}>()

function onReadonlyChange(v: boolean) {
  emit('toggleConfig', props.field, 'readonly', v)
  if (v) emit('toggleConfig', props.field, 'required', false)
}
</script>

<template>
  <NeutralContainer class="space-y-2 mb-2 cursor-grab active:cursor-grabbing">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5"/>
        <span class="font-medium text-sm">{{ field.name }}</span>
        <span class="text-xs text-(--text-muted)">{{ typeLabel }}</span>
        <span v-if="!isSection(field)" class="text-xs text-(--text-muted)">{{ widthLabel(t, widthOf(field)) }}</span>
      </div>
      <div class="flex items-center gap-1">
        <EditButton @click="emit('edit', field)"/>
        <DeleteButton @click="emit('delete', field)"/>
      </div>
    </div>
    <div class="flex flex-wrap gap-3 text-xs">
      <label class="flex items-center gap-1">
        <CompactToggle :model-value="!!parseFieldConfig(field.config).required" :disabled="!!parseFieldConfig(field.config).readonly"
                     @update:model-value="v => emit('toggleConfig', field, 'required', v)"/>
        {{ t('membersConfig.fieldRequired') }}
      </label>
      <label class="flex items-center gap-1">
        <CompactToggle :model-value="!!parseFieldConfig(field.config).readonly"
                     @update:model-value="onReadonlyChange"/>
        {{ t('membersConfig.fieldReadonly') }}
      </label>
      <label class="flex items-center gap-1">
        <CompactToggle :model-value="!!parseFieldConfig(field.config).notifyOnChange"
                     @update:model-value="v => emit('toggleConfig', field, 'notifyOnChange', v)"/>
        {{ t('membersConfig.fieldNotifyOnChange') }}
      </label>
      <label class="flex items-center gap-1">
        <CompactToggle :model-value="!!parseFieldConfig(field.config).overview"
                     @update:model-value="v => emit('toggleConfig', field, 'overview', v)"/>
        {{ t('membersConfig.fieldOverview') }}
      </label>
      <label class="flex items-center gap-1">
        <CompactToggle :model-value="!!field.keepOnArchive"
                     @update:model-value="v => emit('toggleKeepOnArchive', field, v)"/>
        {{ t('membersConfig.fieldKeepOnArchive') }}
      </label>
    </div>
  </NeutralContainer>
</template>
