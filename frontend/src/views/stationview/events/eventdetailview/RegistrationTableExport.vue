/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ExportSeparatorField from '@/components/documents/ExportSeparatorField.vue'
import type {ExportSeparator} from '@/util/exportFormat'

/** Asks which kind of file the table of sign-ups should be taken away as. */
const open = defineModel<boolean>({required: true})

const emit = defineEmits<{
  download: [format: 'csv' | 'pdf', separator: ExportSeparator]
}>()

const {t} = useI18n()

const format = ref<'csv' | 'pdf'>('csv')
const separator = ref<ExportSeparator>('semicolon')
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ t('memberTable.export') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('memberTable.exportHint') }}</MutedText>
      <div class="space-y-2">
        <FieldLabel>{{ t('memberTable.format') }}</FieldLabel>
        <div class="flex items-center gap-4">
          <FieldLabel inline class="cursor-pointer">
            <RadioInput v-model="format" value="csv"/>
            {{ t('memberTable.csv') }}
          </FieldLabel>
          <FieldLabel inline class="cursor-pointer">
            <RadioInput v-model="format" value="pdf"/>
            {{ t('memberTable.pdf') }}
          </FieldLabel>
        </div>
      </div>
      <ExportSeparatorField v-if="format === 'csv'" v-model="separator"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton
            :icon="['fas', 'download']"
            data-testid="registration-table-download"
            @click="emit('download', format, separator)"
        >
          {{ t('memberTable.export') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
