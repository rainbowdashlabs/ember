/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingListField } from '@/api/types'
import FieldRow from './FieldRow.vue'

const props = defineProps<{
  listName: string
  fields: WaitingListField[]
  fieldTypeLabel: (type: string) => string
  parseConfig: (configStr: string | undefined | null) => Record<string, unknown>
}>()

defineEmits<{
  (e: 'add'): void
  (e: 'edit', field: WaitingListField): void
  (e: 'delete', field: WaitingListField): void
  (e: 'move', index: number, direction: -1 | 1): void
}>()

const { t } = useI18n()

const sortedFields = computed(() =>
  [...props.fields].sort((a, b) => a.position - b.position),
)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('waitingList.fields') }} - {{ listName }}</SubHeader>
      <PrimaryButton :icon="['fas', 'plus']" @click="$emit('add')">
        {{ t('waitingList.addField') }}
      </PrimaryButton>
    </div>

    <p class="text-sm text-(--text-muted)">{{ t('waitingList.fieldsHint') }}</p>

    <EmptyState compact v-if="sortedFields.length === 0">{{ t('waitingList.noFields') }}</EmptyState>

    <div class="space-y-2">
      <FieldRow
        v-for="(field, index) in sortedFields"
        :key="field.id"
        :field="field"
        :index="index"
        :total="sortedFields.length"
        :field-type-label="fieldTypeLabel"
        :parse-config="parseConfig"
        @move="(i, d) => $emit('move', i, d)"
        @edit="(f) => $emit('edit', f)"
        @delete="(f) => $emit('delete', f)"
      />
    </div>
  </NeutralContainer>
</template>
