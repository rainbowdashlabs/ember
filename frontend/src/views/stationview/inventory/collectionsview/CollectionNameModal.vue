/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'

const show = defineModel<boolean>('show', {required: true})
const name = defineModel<string>('name', {required: true})
const note = defineModel<string>('note', {required: true})

defineProps<{
  /** Whether the dialog is creating a collection rather than renaming one. */
  creating: boolean
  save: () => Promise<void>
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="show" size="md">
    <div class="space-y-4">
      <FieldLabel>{{ t('inventory.collections.name') }}</FieldLabel>
      <TextInput v-model="name" data-testid="collection-name" :placeholder="t('inventory.collections.namePlaceholder')"/>
      <FieldLabel>{{ t('inventory.collections.note') }}</FieldLabel>
      <TextAreaInput v-model="note" data-testid="collection-note"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton data-cancel @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton data-testid="collection-save" :action="save">
          {{ creating ? t('inventory.collections.create') : t('common.save') }}
        </SaveButton>
      </div>
    </div>
  </Modal>
</template>
