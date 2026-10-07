/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import {useBlockEditorOptions} from '@/composables/useBlockEditorOptions'

const open = defineModel<boolean>({required: true})

const emit = defineEmits<{
  (e: 'select', columns: number): void
}>()

const {t} = useI18n()
const options = useBlockEditorOptions()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ t('stationPages.editor.addRowTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationPages.editor.addRowHint') }}</p>
      <div class="grid gap-2" :style="{gridTemplateColumns: `repeat(${options.maxColumns}, minmax(0, 1fr))`}">
        <BaseButton
            v-for="n in options.maxColumns" :key="n"
            class="flex-col gap-2 !p-3 border border-[var(--border)] hover:border-primary hover:bg-primary/5"
            :data-testid="`add-row-columns-${n}`"
            @click="emit('select', n)"
        >
          <span class="inline-flex gap-1 h-8 w-full">
            <span v-for="i in n" :key="i" class="flex-1 rounded-sm bg-primary/20"/>
          </span>
          <span class="text-xs font-medium">{{ n }}</span>
        </BaseButton>
      </div>
      <div class="flex justify-end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
