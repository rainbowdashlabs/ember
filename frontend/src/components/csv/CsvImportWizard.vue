/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, useSlots} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {CsvImportSteps, type CsvImportController} from '@/composables/useCsvImport'

const props = defineProps<{
  importer: CsvImportController
  accept?: string
}>()

const {t} = useI18n()
const slots = useSlots()

const importer = props.importer
const {step, loading, error, separator, fileName, lineCount, hasFile} = importer

const separatorChoices = computed(() => [
  {label: ';', value: ';'},
  {label: ',', value: ','},
  {label: t('csvImport.tab'), value: '\t'},
])

const hasPreviewStep = computed(() => !!slots.preview)

function advanceFromMapping() {
  if (hasPreviewStep.value) importer.showPreview()
  else importer.commit()
}
</script>

<template>
  <div class="space-y-6">
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <NeutralContainer v-if="step === CsvImportSteps.UPLOAD" class="space-y-4">
      <SubHeader>{{ t('csvImport.uploadTitle') }}</SubHeader>
      <div class="flex items-center gap-4 flex-wrap">
        <FileUploadButton :accept="accept ?? '.csv,.tsv,.txt'" @select="importer.selectFile">
          {{ t('csvImport.chooseFile') }}
        </FileUploadButton>
        <span v-if="fileName" class="text-sm">
          <font-awesome-icon :icon="['fas', 'check']" class="text-success mr-1"/>
          {{ fileName }} ({{ lineCount }} {{ t('csvImport.rows') }})
        </span>
      </div>
      <div class="flex items-center gap-6 flex-wrap">
        <div class="flex items-center gap-2">
          <FieldLabel hint>{{ t('csvImport.separator') }}</FieldLabel>
          <SelectInput v-model="separator" class="w-24">
            <option v-for="option in separatorChoices" :key="option.label" :value="option.value">{{ option.label }}</option>
          </SelectInput>
        </div>
        <slot name="uploadOptions"/>
      </div>
      <PrimaryButton :disabled="!hasFile || loading" @click="importer.parse()">
        {{ loading ? t('common.loading') : t('csvImport.next') }}
      </PrimaryButton>
    </NeutralContainer>

    <template v-else-if="step === CsvImportSteps.MAPPING">
      <slot name="mapping"/>
      <div class="flex justify-between gap-3">
        <SecondaryButton @click="importer.goBack()">{{ t('common.back') }}</SecondaryButton>
        <PrimaryButton
            :disabled="loading"
            :icon="['fas', hasPreviewStep ? 'eye' : 'file-import']"
            @click="advanceFromMapping"
        >
          {{ loading ? t('common.loading') : hasPreviewStep ? t('csvImport.preview') : t('csvImport.import') }}
        </PrimaryButton>
      </div>
    </template>

    <template v-else-if="step === CsvImportSteps.PREVIEW">
      <slot name="preview"/>
      <div class="flex justify-between gap-3">
        <SecondaryButton @click="importer.goBack()">{{ t('common.back') }}</SecondaryButton>
        <PrimaryButton :disabled="loading" :icon="['fas', 'file-import']" @click="importer.commit()">
          {{ loading ? t('common.loading') : t('csvImport.import') }}
        </PrimaryButton>
      </div>
    </template>

    <slot v-else name="done"/>

    <Spinner v-if="loading" size="lg"/>
  </div>
</template>
