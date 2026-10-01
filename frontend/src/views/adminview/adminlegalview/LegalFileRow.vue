/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {LegalFileEntry} from '@/api/generated/schema'

const {t} = useI18n()

defineProps<{
  file: LegalFileEntry
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

const nameId = useId()
</script>

<template>
  <NeutralContainer class="space-y-2" :class="{ 'opacity-50': !file.enabled }">
    <div class="flex items-center gap-2">
      <ToggleInput :model-value="file.enabled" @update:model-value="emit('toggle')"/>
      <SubHeader :id="nameId" class="flex-1 min-w-0">
        {{ file.generated ? t('adminSettings.legal.generatedName') : (file.displayName || file.filename) }}
      </SubHeader>
      <IconButton :icon="['fas', 'chevron-up']" :label="t('adminSettings.legal.moveUp')"
                  :disabled="index === 0" @click="emit('moveUp')"/>
      <IconButton :icon="['fas', 'chevron-down']" :label="t('adminSettings.legal.moveDown')"
                  :disabled="index === total - 1" @click="emit('moveDown')"/>
      <DeleteButton v-if="!file.generated" @click="emit('delete')"/>
    </div>
    <MutedText v-if="file.generated" size="sm">{{ t('adminSettings.legal.generatedHint') }}</MutedText>
    <TextAreaInput
        v-if="!file.generated"
        :model-value="file.content"
        :aria-labelledby="nameId"
        class="min-h-[200px] font-mono text-sm"
        :placeholder="t('adminSettings.legal.contentPlaceholder')"
        @update:model-value="value => emit('updateContent', value ?? '')"
    />
    <pre
        v-else
        class="w-full rounded-lg border border-(--border) bg-(--bg) text-(--text-muted) p-3 max-h-[200px] overflow-auto font-mono text-xs whitespace-pre-wrap"
    >{{ file.content }}</pre>
  </NeutralContainer>
</template>
