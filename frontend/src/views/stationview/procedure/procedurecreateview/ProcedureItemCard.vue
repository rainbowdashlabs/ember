/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

const props = defineProps<{
  item: {
    tempId: number
    title: string
    description: string
    isPublic: boolean
    userAssigned: boolean
    dependsOn: number[]
  }
  allItems: { tempId: number; title: string }[]
}>()

const emit = defineEmits<{
  remove: []
}>()

function availableDeps() {
  return props.allItems.filter(i => i.tempId !== props.item.tempId && !props.item.dependsOn.includes(i.tempId))
}

function addDependency(value: string | number | null | undefined) {
  if (value) props.item.dependsOn = [...props.item.dependsOn, Number(value)]
}

function removeDependency(depId: number) {
  props.item.dependsOn = props.item.dependsOn.filter(d => d !== depId)
}
</script>

<template>
  <div class="p-3 rounded-lg border border-(--border) space-y-2">
    <div class="flex items-start gap-3">
      <div class="flex-1 min-w-0 space-y-2">
        <TextInput v-model="item.title" :placeholder="t('procedures.itemTitle')"/>
        <TextAreaInput v-model="item.description" :placeholder="t('procedures.itemDescription')" :rows="2"/>
      </div>
      <div class="flex items-center gap-1 shrink-0">
        <DeleteButton @click="emit('remove')"/>
      </div>
    </div>
    <div class="flex items-center gap-4 text-sm">
      <label class="flex items-center gap-1.5 cursor-pointer">
        <ToggleInput v-model="item.userAssigned"/>
        <span class="text-(--text-muted)">{{ t('procedures.userCanCheck') }}</span>
      </label>
      <label class="flex items-center gap-1.5 cursor-pointer">
        <ToggleInput v-model="item.isPublic"/>
        <span class="text-(--text-muted)">{{ t('procedures.itemPublic') }}</span>
      </label>
    </div>
    <div class="text-sm">
      <FieldLabel class="mb-1">{{ t('procedures.dependsOn') }}</FieldLabel>
      <div v-if="item.dependsOn.length > 0" class="flex flex-wrap gap-1 mb-1">
        <span v-for="depId in item.dependsOn" :key="depId"
              class="inline-flex items-center gap-1 bg-(--bg-light-accent) dark:bg-(--bg-dark-accent) rounded px-2 py-0.5 text-xs">
          {{ allItems.find(i => i.tempId === depId)?.title || '?' }}
          <IconButton :icon="['fas', 'xmark']" label="Remove" class="!p-0" @click="removeDependency(depId)"/>
        </span>
      </div>
      <SelectInput v-if="availableDeps().length > 0" model-value="" class="w-full text-xs"
                   @update:model-value="addDependency">
        <option value="">{{ t('procedures.addDependency') }}</option>
        <option v-for="other in availableDeps()" :key="other.tempId" :value="String(other.tempId)">
          {{ other.title || t('procedures.untitledItem') }}
        </option>
      </SelectInput>
    </div>
  </div>
</template>
