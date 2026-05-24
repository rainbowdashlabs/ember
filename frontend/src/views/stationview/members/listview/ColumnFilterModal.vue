/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

const props = defineProps<{
  columnLabel: string
  values: string[]
  selectedValues: Set<string>
  includeEmpty: boolean
}>()

const emit = defineEmits<{
  apply: [selected: Set<string>, includeEmpty: boolean]
  close: []
}>()

const model = defineModel<boolean>({ default: false })

const localSelected = ref<Set<string>>(new Set())
const localIncludeEmpty = ref(false)

watch(model, (open) => {
  if (open) {
    localSelected.value = new Set(props.selectedValues)
    localIncludeEmpty.value = props.includeEmpty
  }
})

function toggleValue(val: string) {
  const s = new Set(localSelected.value)
  if (s.has(val)) { s.delete(val) } else { s.add(val) }
  localSelected.value = s
}

function selectAll() {
  localSelected.value = new Set(props.values)
  localIncludeEmpty.value = false
}

function selectNone() {
  localSelected.value = new Set()
  localIncludeEmpty.value = false
}

function apply() {
  emit('apply', localSelected.value, localIncludeEmpty.value)
  model.value = false
}

function close() {
  emit('close')
  model.value = false
}
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4">
      <SubHeader class="text-lg font-semibold">{{ t('membersList.filterBy', { column: columnLabel }) }}</SubHeader>

      <div class="flex gap-2 text-xs">
        <SecondaryButton @click="selectAll">{{ t('membersList.filterSelectAll') }}</SecondaryButton>
        <SecondaryButton @click="selectNone">{{ t('membersList.filterSelectNone') }}</SecondaryButton>
      </div>

      <div class="max-h-64 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
        <!-- Empty value option -->
        <label class="flex items-center gap-2 px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50 cursor-pointer text-sm">
          <input
              v-model="localIncludeEmpty"
              type="checkbox"
              class="h-4 w-4 rounded accent-primary"
          />
          <span class="italic text-(--text-muted)">{{ t('membersList.filterEmpty') }}</span>
        </label>

        <!-- Value options -->
        <label
            v-for="val in values"
            :key="val"
            class="flex items-center gap-2 px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50 cursor-pointer text-sm"
        >
          <input
              :checked="localSelected.has(val)"
              type="checkbox"
              class="h-4 w-4 rounded accent-primary"
              @change="toggleValue(val)"
          />
          <span>{{ val }}</span>
        </label>

        <div v-if="values.length === 0" class="text-center text-(--text-muted) text-xs py-2">
          {{ t('membersList.filterNoValues') }}
        </div>
      </div>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton @click="apply">{{ t('membersList.filterApply') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
