/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import AuthImage from '@/components/display/AuthImage.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * The photo of an inventory or a kind: shown, replaced by picking another, or taken away.
 *
 * <p>Picking a new photo replaces the old one in one step, there is no need to remove it first. The
 * field sends and removes the photo itself through the two calls it is handed, and hands its owner the
 * new key, so the owner can show the photo elsewhere at once.
 */
const props = defineProps<{
  /** The address of the photo as it stands, or null where there is none. */
  src: string | null
  /** Sends a picked photo and answers with the key it is now kept under. */
  upload: (file: File) => Promise<string>
  /** Takes the photo away. */
  remove: () => Promise<void>
  /** A line under the field describing what the photo is used for. */
  hint?: string
}>()

const emit = defineEmits<{
  changed: [pictureKey: string | null]
}>()

const {t} = useI18n()
const inputId = useId()
const input = ref<HTMLInputElement | null>(null)

const send = useAsyncAction(async (file: File) => {
  emit('changed', await props.upload(file))
})

const drop = useAsyncAction(async () => {
  await props.remove()
  emit('changed', null)
})

function pick(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (file) void send.run(file)
}

function choose() {
  input.value?.click()
}
</script>

<template>
  <div class="space-y-2" data-testid="inventory-picture-field">
    <FieldLabel :for="inputId">{{ t('inventory.picture.label') }}</FieldLabel>
    <div class="flex flex-wrap items-center gap-3">
      <AuthImage v-if="src" :src="src" alt=""
                 class="h-24 w-24 rounded-theme border border-(--border) object-cover"/>
      <div v-else class="flex h-24 w-24 items-center justify-center rounded-theme border border-dashed border-(--border)">
        <font-awesome-icon :icon="['fas', 'image']" class="text-(--text-muted)"/>
      </div>
      <Spinner v-if="send.running.value || drop.running.value" size="sm"/>
      <div class="flex flex-col gap-2">
        <SecondaryButton :disabled="send.running.value || drop.running.value" :icon="['fas', 'upload']"
                         data-testid="inventory-picture-pick" @click="choose">
          {{ src ? t('inventory.picture.replace') : t('inventory.picture.add') }}
        </SecondaryButton>
        <SecondaryButton v-if="src" :disabled="send.running.value || drop.running.value" :icon="['fas', 'trash']"
                         data-testid="inventory-picture-remove" @click="drop.run()">
          {{ t('inventory.picture.remove') }}
        </SecondaryButton>
      </div>
    </div>
    <input :id="inputId" ref="input" accept="image/png,image/jpeg,image/webp" class="hidden" type="file"
           @change="pick"/>
    <MutedText v-if="hint" size="xs" tag="p">{{ hint }}</MutedText>
    <FailureAlert :failure="send.failure.value ?? drop.failure.value"/>
  </div>
</template>
