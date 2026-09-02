/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'

/**
 * Picking the picture of a found item, from the gallery or straight from the camera.
 *
 * Both inputs take whatever the device offers rather than the three formats the endpoint keeps:
 * a phone writes its photos in its own format, and narrowing the picker there only hides them.
 * What is picked is made smaller and rewritten on the way out instead.
 */
const file = defineModel<File | null>({required: true})

const emit = defineEmits<{
  (e: 'clear'): void
}>()

const {t} = useI18n()
const preview = ref<string | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const cameraInputRef = ref<HTMLInputElement | null>(null)

function pick(event: Event) {
  const input = event.target as HTMLInputElement
  const picked = input.files?.[0]
  input.value = ''
  if (!picked) return
  revoke()
  file.value = picked
  preview.value = URL.createObjectURL(picked)
}

function clear() {
  revoke()
  file.value = null
  emit('clear')
}

function revoke() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = null
}

defineExpose({clear})
onBeforeUnmount(revoke)
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('lostAndFound.image') }}</FieldLabel>
    <div v-if="preview" class="relative">
      <img :src="preview" alt="" class="w-full max-h-48 object-cover rounded-lg"/>
      <IconButton
          :icon="['fas', 'xmark']"
          :label="t('common.remove')"
          class="absolute top-2 right-2 bg-error text-error-text rounded-full h-6 w-6 hover:bg-error/80"
          @click="clear"
      />
    </div>
    <div v-else class="flex gap-2">
      <SecondaryButton :icon="['fas', 'upload']" class="flex-1" @click="fileInputRef?.click()">
        {{ t('lostAndFound.uploadImage') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'camera']" class="flex-1" @click="cameraInputRef?.click()">
        {{ t('lostAndFound.takePhoto') }}
      </SecondaryButton>
    </div>
    <input ref="fileInputRef" type="file" accept="image/*" class="hidden" @change="pick"/>
    <input ref="cameraInputRef" type="file" accept="image/*" capture="environment" class="hidden" @change="pick"/>
  </div>
</template>
