/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import DragList from '@/components/input/DragList.vue'
import type {ChecklistColumnDto} from '@/api/types'

const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  initialName: string
  initialDescription: string
  initialColumns: ChecklistColumnDto[]
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: {name: string; description: string; orderedColumnIds: number[]}): void
}>()

const {t} = useI18n()

const name = ref(props.initialName)
const description = ref(props.initialDescription)
const orderedColumns = ref<ChecklistColumnDto[]>([...props.initialColumns])

watch(visible, (value) => {
  if (value) {
    name.value = props.initialName
    description.value = props.initialDescription
    orderedColumns.value = [...props.initialColumns]
  }
})

function onReorder(fromIndex: number, toIndex: number) {
  const [moved] = orderedColumns.value.splice(fromIndex, 1)
  orderedColumns.value.splice(toIndex, 0, moved)
}

function submit() {
  const trimmed = name.value.trim()
  if (!trimmed) return
  emit('submit', {
    name: trimmed,
    description: description.value.trim(),
    orderedColumnIds: orderedColumns.value.map(c => c.id),
  })
}
</script>

<template>
  <Modal v-model="visible" size="md">
    <div class="space-y-4">
      <SubHeader>{{ t('checklist.editChecklist') }}</SubHeader>

      <div>
        <FieldLabel>{{ t('checklist.name') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('checklist.namePlaceholder')"/>
      </div>

      <div>
        <FieldLabel>{{ t('checklist.description') }}</FieldLabel>
        <TextAreaInput
            v-model="description"
            :placeholder="t('checklist.descriptionPlaceholder')"
            rows="3"
        />
      </div>

      <div>
        <FieldLabel>{{ t('checklist.columnOrder') }}</FieldLabel>
        <p class="text-xs text-(--text-muted) mb-2">{{ t('checklist.columnOrderHint') }}</p>
        <EmptyState v-if="orderedColumns.length === 0">
          {{ t('checklist.columnsEmpty') }}
        </EmptyState>
        <DragList
            v-else
            :items="orderedColumns"
            :key-fn="(c) => c.id"
            @reorder="onReorder"
        >
          <template #default="{item, index}">
            <div class="flex items-center gap-2 px-2 py-1.5 border border-bg-light-accent dark:border-bg-dark-accent rounded-theme bg-bg-light dark:bg-bg-dark cursor-grab active:cursor-grabbing">
              <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted)"/>
              <span class="text-xs text-(--text-muted) tabular-nums w-6">{{ index + 1 }}.</span>
              <div class="flex-1 min-w-0">
                <div class="font-medium truncate">{{ item.label }}</div>
                <div v-if="item.description" class="text-xs text-(--text-muted) truncate">{{ item.description }}</div>
              </div>
            </div>
          </template>
        </DragList>
      </div>

      <div class="flex justify-end gap-2 pt-2">
        <SecondaryButton @click="visible = false">{{ t('checklist.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !name.trim()" @click="submit">
          {{ t('checklist.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
