/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {PasskeyEntry} from '@/api/passkeys'

const {t} = useI18n()

const target = defineModel<PasskeyEntry | null>({required: true})

const emit = defineEmits<{
  (e: 'save', id: number, label: string): void
}>()

const label = ref('')

watch(target, (entry) => {
  if (entry) label.value = entry.label
})

function save() {
  if (!target.value || !label.value.trim()) return
  emit('save', target.value.id, label.value.trim())
}
</script>

<template>
  <Modal :model-value="target !== null" size="sm" @update:model-value="target = null">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('passkeys.section.renameTitle') }}</SubHeader>
      <TextInput v-model="label" :placeholder="t('passkeys.section.labelPlaceholder')"/>
      <div class="flex justify-between gap-2">
        <SecondaryButton type="button" @click="target = null">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton type="button" :disabled="!label.trim()" @click="save">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
