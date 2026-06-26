/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { WaitingListField } from '@/api/types'

const props = defineProps<{
  field: WaitingListField
  index: number
  total: number
  fieldTypeLabel: (type: string) => string
  parseConfig: (configStr: string | undefined | null) => Record<string, unknown>
}>()

defineEmits<{
  (e: 'move', index: number, direction: -1 | 1): void
  (e: 'edit', field: WaitingListField): void
  (e: 'delete', field: WaitingListField): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center gap-2 rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30">
    <div class="flex flex-col gap-1">
      <IconButton
        icon="chevron-up"
        :label="t('waitingList.moveUp')"
        :disabled="index === 0"
        @click="$emit('move', index, -1)"
      />
      <IconButton
        icon="chevron-down"
        :label="t('waitingList.moveDown')"
        :disabled="index === total - 1"
        @click="$emit('move', index, 1)"
      />
    </div>

    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-medium">{{ field.name }}</span>
        <SecondaryBadge>{{ fieldTypeLabel(field.fieldType) }}</SecondaryBadge>
        <PrimaryBadge v-if="field.required">{{ t('waitingList.required') }}</PrimaryBadge>
      </div>
      <MutedText tag="div" class="mt-1" v-if="field.fieldType === 'ENUM'">
        {{ t('waitingList.options') }}: {{ parseConfig(field.config).options ? (parseConfig(field.config).options as string[]).join(', ') : '-' }}
      </MutedText>
    </div>

    <div class="flex items-center gap-1">
      <EditButton @click="$emit('edit', field)" />
      <DeleteButton @click="$emit('delete', field)" />
    </div>
  </div>
</template>
