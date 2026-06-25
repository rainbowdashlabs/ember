/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'choose', target: 'existing' | 'new'): void
}>()

const {t} = useI18n()
const localOpen = ref(false)

function pick(target: 'existing' | 'new') {
  emit('choose', target)
  emit('update:open', false)
}
</script>

<template>
  <Modal :model-value="open" size="sm" @update:model-value="(v) => emit('update:open', v)">
    <SubHeader class="mb-2">{{ t('inventory.storage.addChoice.title') }}</SubHeader>
    <p class="text-sm text-(--text-muted) mb-4">{{ t('inventory.storage.addChoice.intro') }}</p>
    <div class="flex flex-col gap-3">
      <button
          type="button"
          class="flex items-start gap-3 p-3 rounded-theme border border-(--bg-accent) hover:bg-(--bg-accent) text-left transition-colors"
          @click="pick('existing')"
      >
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="mt-1 text-primary" />
        <span class="flex-1">
          <span class="block font-medium">{{ t('inventory.storage.addChoice.existing') }}</span>
          <span class="block text-xs text-(--text-muted)">{{ t('inventory.storage.addChoice.existingHint') }}</span>
        </span>
      </button>
      <button
          type="button"
          class="flex items-start gap-3 p-3 rounded-theme border border-(--bg-accent) hover:bg-(--bg-accent) text-left transition-colors"
          @click="pick('new')"
      >
        <font-awesome-icon :icon="['fas', 'plus']" class="mt-1 text-primary" />
        <span class="flex-1">
          <span class="block font-medium">{{ t('inventory.storage.addChoice.new') }}</span>
          <span class="block text-xs text-(--text-muted)">{{ t('inventory.storage.addChoice.newHint') }}</span>
        </span>
      </button>
    </div>
    <div class="flex justify-end mt-4">
      <SecondaryButton @click="emit('update:open', false)">{{ t('common.cancel') }}</SecondaryButton>
    </div>
  </Modal>
</template>
