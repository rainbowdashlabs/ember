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
import AudienceCount from './AudienceCount.vue'
import WritabilitySelect from './WritabilitySelect.vue'
import {useFieldsCapabilities, type WritabilityName} from '@/composables/useFieldsConfig'
import {type ProfileField, parseFieldConfig} from '@/api/profileFields'
import {holdsAnswer, isSection, widthOf} from '@/components/profilefields/fieldLayout'
import {widthLabel} from '../fieldTypes'

const {t} = useI18n()

defineProps<{
  field: ProfileField
  typeLabel: string
  audiences: number
  selected: boolean
  checked: boolean
}>()

const emit = defineEmits<{
  select: [field: ProfileField]
  toggleChecked: [field: ProfileField]
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
  toggleRequired: [field: ProfileField, value: boolean]
  toggleReadonly: [field: ProfileField, value: boolean]
  setWritability: [field: ProfileField, level: WritabilityName]
}>()

const capabilities = useFieldsCapabilities()
</script>

<template>
  <NeutralContainer
      :class="selected ? 'ring-2 ring-primary' : ''"
      :data-testid="`field-row-${field.name}`"
      class="space-y-2 mb-2"
      @click="emit('select', field)">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <input
            type="checkbox"
            class="cursor-pointer"
            :checked="checked"
            :aria-label="t('membersConfig.batch.toggleRow')"
            :data-testid="`field-check-${field.name}`"
            @click.stop="emit('toggleChecked', field)"/>
        <span class="font-medium text-sm">{{ field.name }}</span>
        <span class="text-xs text-(--text-muted)">{{ typeLabel }}</span>
        <span v-if="!isSection(field)" class="text-xs text-(--text-muted)">{{ widthLabel(t, widthOf(field)) }}</span>
        <AudienceCount :count="audiences"/>
      </div>
      <div class="flex items-center gap-1" @click.stop>
        <EditButton @click="emit('edit', field)"/>
        <DeleteButton @click="emit('delete', field)"/>
      </div>
    </div>
    <div v-if="holdsAnswer(field)" class="flex flex-wrap gap-3 text-xs" @click.stop>
      <label class="flex items-center gap-1">
        <CompactToggle :model-value="!!field.required"
                       @update:model-value="v => emit('toggleRequired', field, v)"/>
        {{ t('membersConfig.fieldRequired') }}
      </label>
      <label v-if="capabilities.writability" class="flex items-center gap-1">
        <span>{{ t('membersConfig.writability.column') }}</span>
        <WritabilitySelect :field="field" @set="(f, level) => emit('setWritability', f, level)"/>
      </label>
      <label v-else class="flex items-center gap-1">
        <CompactToggle :model-value="!!field.readonly"
                       @update:model-value="v => emit('toggleReadonly', field, v)"/>
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
