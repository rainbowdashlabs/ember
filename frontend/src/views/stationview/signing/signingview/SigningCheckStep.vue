/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SigningStepFrame from './SigningStepFrame.vue'
import SigningStepNav from './SigningStepNav.vue'
import {signerShort, type SigningDocumentGroup} from './batchFlow'

/**
 * The last look before confirming: how many fields in how many documents are signed now, and the short
 * list of them again, each with who signs it.
 */
const props = defineProps<{
  groups: SigningDocumentGroup[]
  position: number
  total: number
}>()

defineEmits<{next: []; back: []}>()

const {t} = useI18n()

const summary = computed(() => {
  const fields = props.groups.reduce((sum, group) => sum + group.fields.length, 0)
  return t('signing.flow.check.summary', {
    fields: fields === 1 ? t('signing.flow.check.oneField') : t('signing.flow.check.fields', {count: fields}),
    documents: props.groups.length === 1
        ? t('signing.flow.check.oneDocument')
        : t('signing.flow.check.documents', {count: props.groups.length}),
  })
})
</script>

<template>
  <SigningStepFrame :title="t('signing.flow.check.heading')" :position="position" :total="total">
    <p data-testid="signing-check-summary">{{ summary }}</p>
    <ul class="list-disc pl-5 space-y-1 text-sm" data-testid="signing-check-list">
      <template v-for="group in groups" :key="group.requestUid">
        <li v-for="field in group.fields" :key="field.fieldId">
          {{ t('signing.flow.overview.line', {document: group.title, who: signerShort(field, t)}) }}
        </li>
      </template>
    </ul>
    <template #actions>
      <SigningStepNav @next="$emit('next')" @back="$emit('back')"/>
    </template>
  </SigningStepFrame>
</template>
