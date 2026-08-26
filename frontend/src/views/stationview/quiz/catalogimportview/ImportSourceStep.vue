/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AiAnswerOptions from '../csvimportview/AiAnswerOptions.vue'
import CatalogFormatReference from './formatreference/CatalogFormatReference.vue'

const separator = defineModel<string>('separator', {required: true})
const aiEnabled = defineModel<boolean>('aiEnabled', {required: true})
const aiCount = defineModel<number>('aiCount', {required: true})
const aiPrompt = defineModel<string>('aiPrompt', {required: true})

defineProps<{
  fileName: string
  isSheet: boolean
  loading: boolean
  offerAi: boolean
}>()

const emit = defineEmits<{
  select: [file: File]
  next: []
}>()

const {t} = useI18n()

const showFormat = ref(false)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('quiz.import.sourceTitle') }}</SubHeader>
    <MutedText>{{ t('quiz.import.sourceHint') }}</MutedText>

    <div class="flex items-center gap-4 flex-wrap">
      <FileUploadButton accept=".json,.csv,.tsv,.txt" @select="emit('select', $event)">
        {{ t('csvImport.chooseFile') }}
      </FileUploadButton>
      <span v-if="fileName" class="text-sm">
        <font-awesome-icon :icon="['fas', 'check']" class="text-success mr-1" />
        {{ fileName }}
      </span>
    </div>

    <div v-if="isSheet" class="flex items-center gap-2">
      <FieldLabel hint>{{ t('csvImport.separator') }}</FieldLabel>
      <SelectInput v-model="separator" class="w-24">
        <option value=";">;</option>
        <option value=",">,</option>
        <option value="&#9;">{{ t('csvImport.tab') }}</option>
      </SelectInput>
    </div>

    <AiAnswerOptions
        v-if="offerAi && isSheet"
        v-model:enabled="aiEnabled"
        v-model:count="aiCount"
        v-model:prompt="aiPrompt"
    />

    <PrimaryButton :disabled="!fileName || loading" @click="emit('next')">
      {{ loading ? t('common.loading') : t('csvImport.next') }}
    </PrimaryButton>

    <SecondaryButton
        :icon="['fas', showFormat ? 'chevron-up' : 'circle-question']"
        @click="showFormat = !showFormat"
    >
      {{ showFormat ? t('quiz.format.hide') : t('quiz.format.show') }}
    </SecondaryButton>

    <CatalogFormatReference v-if="showFormat" offer-downloads />
  </NeutralContainer>
</template>
