/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

defineProps<{
  accept?: string
  label?: string
}>()

const emit = defineEmits<{
  select: [file: File]
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const fileName = ref('')

function openPicker() {
  fileInput.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    fileName.value = input.files[0].name
    emit('select', input.files[0])
  }
}
</script>

<template>
  <div class="flex items-center gap-3">
    <SecondaryButton @click="openPicker">
      <font-awesome-icon :icon="['fas', 'upload']" class="mr-1"/>
      {{ label ?? t('common.selectFile') }}
    </SecondaryButton>
    <span v-if="fileName" class="text-sm text-(--text) truncate">{{ fileName }}</span>
    <span v-else class="text-sm text-(--text-muted)">{{ t('common.noFileSelected') }}</span>
    <input ref="fileInput" type="file" :accept="accept" class="hidden" @change="onFileChange"/>
  </div>
</template>
