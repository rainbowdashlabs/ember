/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {LegalFile} from '@/api/adminSettings'

const {t} = useI18n()

const props = defineProps<{
  file: LegalFile
  index: number
  total: number
}>()

const emit = defineEmits<{
  toggle: []
  moveUp: []
  moveDown: []
  delete: []
  updateContent: [value: string]
}>()

function onInput(event: Event) {
  emit('updateContent', (event.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <NeutralContainer class="space-y-2" :class="{ 'opacity-50': !file.enabled }">
    <div class="flex items-center gap-2">
      <ToggleInput :model-value="file.enabled" @update:model-value="emit('toggle')"/>
      <SubHeader class="flex-1 min-w-0">{{ file.displayName || file.filename }}</SubHeader>
      <IconButton :icon="['fas', 'chevron-up']" :label="t('adminSettings.legal.moveUp')"
                  :disabled="index === 0" @click="emit('moveUp')"/>
      <IconButton :icon="['fas', 'chevron-down']" :label="t('adminSettings.legal.moveDown')"
                  :disabled="index === total - 1" @click="emit('moveDown')"/>
      <DeleteButton @click="emit('delete')"/>
    </div>
    <textarea
        :value="file.content"
        class="w-full rounded-lg border border-(--border) bg-(--bg) text-(--text) p-3 min-h-[200px] font-mono text-sm outline-none resize-y focus:ring-2 focus:ring-primary/50"
        :placeholder="t('adminSettings.legal.contentPlaceholder')"
        @input="onInput"
    />
  </NeutralContainer>
</template>
