/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'

const open = defineModel<boolean>({required: true})

const emit = defineEmits<{
  (e: 'select', columns: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ t('stationPages.editor.addRowTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationPages.editor.addRowHint') }}</p>
      <div class="grid grid-cols-4 gap-2">
        <button
            v-for="n in 4" :key="n"
            class="flex flex-col items-center gap-2 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors p-3"
            @click="emit('select', n)"
        >
          <span class="inline-flex gap-1 h-8 w-full">
            <span v-for="i in n" :key="i" class="flex-1 rounded-sm bg-primary/20"/>
          </span>
          <span class="text-xs font-medium">{{ n }}</span>
        </button>
      </div>
      <div class="flex justify-end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
