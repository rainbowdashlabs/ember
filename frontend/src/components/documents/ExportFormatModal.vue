/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import ExportSeparatorField from '@/components/documents/ExportSeparatorField.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {ExportSeparator} from '@/util/exportFormat'

/**
 * What shape a reader wants their export in.
 *
 * <p>The same question is asked wherever a list can leave the product, so it is asked the same way:
 * which format, and for a spreadsheet, what goes between the cells. The separator is worth asking
 * about because guessing wrong puts every row into a single column, which reaches us as "the export
 * is broken" rather than as a question about separators.
 *
 * <p>It only appears for a spreadsheet, because it means nothing for anything else.
 */
const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  /** Which formats this export can produce, in the order they are offered. */
  formats: ('csv' | 'pdf')[]
  exporting?: boolean
}>()

const emit = defineEmits<{export: [format: 'csv' | 'pdf', separator: ExportSeparator]}>()

const {t} = useI18n()

const format = ref<'csv' | 'pdf'>(props.formats[0] ?? 'pdf')
const separator = ref<ExportSeparator>('semicolon')

watch(modelValue, open => {
  if (open) format.value = props.formats[0] ?? 'pdf'
})
</script>

<template>
  <Modal v-model="modelValue" size="sm">
    <div class="space-y-4" data-testid="export-format">
      <SubHeader>{{ t('exportFormat.title') }}</SubHeader>

      <div v-if="props.formats.length > 1" class="space-y-2">
        <FieldLabel>{{ t('exportFormat.format') }}</FieldLabel>
        <div class="flex items-center gap-4">
          <FieldLabel v-for="option in props.formats" :key="option" inline class="cursor-pointer">
            <RadioInput v-model="format" :value="option" :data-testid="`export-format-${option}`"/>
            {{ t(`exportFormat.${option}`) }}
          </FieldLabel>
        </div>
      </div>

      <ExportSeparatorField v-if="format === 'csv'" v-model="separator"/>

      <ButtonRow pair>
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton
            :disabled="props.exporting"
            data-testid="export-format-submit"
            @click="emit('export', format, separator)"
        >
          {{ props.exporting ? t('common.loading') : t('common.export') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
