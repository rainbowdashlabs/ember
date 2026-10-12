/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {WordFormImport} from '@/api/generated/schema'

/**
 * What became of a Word form: which of its fields print the member's data, which are asked when a
 * document is generated, which got no field and have to be placed by hand, and which fonts were drawn
 * in the replacement chosen for them.
 */
const props = defineProps<{
  report: WordFormImport
}>()

const emit = defineEmits<{
  close: []
  open: []
}>()

const {t} = useI18n()

const replacements = computed(() => props.report.fonts
    .map(font => t('documentTemplates.formImport.fontReplaced', {family: font.family, replacement: font.replacement}))
    .join(', '))
</script>

<template>
  <Modal :model-value="true" size="lg" @update:model-value="emit('close')">
    <div class="space-y-4" data-testid="form-import-report">
      <SubHeader>{{ t('documentTemplates.formImport.done', {name: report.template.name}) }}</SubHeader>
      <MutedText tag="p">
        {{ t('documentTemplates.formImport.recognised', {fields: report.recognised.join(', ') || '-'}) }}
      </MutedText>
      <MutedText tag="p">
        {{ t('documentTemplates.formImport.asked', {fields: report.asked.join(', ') || '-'}) }}
      </MutedText>
      <Alert v-if="report.unplaced.length > 0" variant="info">
        {{ t('documentTemplates.formImport.unplaced', {fields: report.unplaced.join(', ')}) }}
      </Alert>
      <Alert v-if="report.fonts.length > 0" variant="info">
        {{ t('documentTemplates.formImport.fonts', {fonts: replacements}) }}
      </Alert>
      <ButtonRow pair align="end">
        <SecondaryButton @click="emit('close')">{{ t('common.close') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'pen']" data-testid="form-import-open" @click="emit('open')">
          {{ t('documentTemplates.formImport.open') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
