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
import SigningDocument from './SigningDocument.vue'
import SigningFieldAgreement from './SigningFieldAgreement.vue'
import type {FillInResponse} from '@/api/generated/schema'
import type {FillInValues} from './fillIns'
import {documentReady, type SigningDocumentGroup} from './batchFlow'

/**
 * One document of the chosen ones: the document itself to read, then for each field the reader signs in
 * it who signs and where, the statement to tick and what to fill in. "Weiter" waits until every box is
 * ticked and every required detail is filled in.
 *
 * <p>What is ticked and typed lives with the flow, not here, so going back and forth keeps it.
 */
const props = defineProps<{
  group: SigningDocumentGroup
  index: number
  count: number
  position: number
  total: number
  fillIns: Readonly<Record<number, FillInResponse[]>>
}>()

const agreed = defineModel<Record<number, boolean>>('agreed', {required: true})
const values = defineModel<Record<number, FillInValues>>('values', {required: true})

defineEmits<{next: []; back: []}>()

const {t} = useI18n()

const title = computed(() => t('signing.flow.document.heading', {
  position: props.index + 1,
  count: props.count,
  title: props.group.title,
}))

/** A field of the document the reader signs, through which the document itself is read. */
const readFrom = computed(() => props.group.fields[0]?.fieldId ?? null)

const ready = computed(() => documentReady(props.group, agreed.value, props.fillIns, values.value))

function agree(fieldId: number, value: boolean) {
  agreed.value = {...agreed.value, [fieldId]: value}
}

function type(fieldId: number, value: FillInValues) {
  values.value = {...values.value, [fieldId]: value}
}
</script>

<template>
  <SigningStepFrame :title="title" :position="position" :total="total">
    <SigningDocument v-if="readFrom" :field-id="readFrom" :title="group.title" :file-name="`${group.title}.pdf`"/>
    <SigningFieldAgreement
        v-for="field in group.fields"
        :key="field.fieldId"
        :field="field"
        :fill-ins="fillIns[field.fieldId] ?? []"
        :agreed="agreed[field.fieldId] ?? false"
        :values="values[field.fieldId] ?? {}"
        @update:agreed="value => agree(field.fieldId, value)"
        @update:values="value => type(field.fieldId, value)"
    />
    <template #actions>
      <SigningStepNav :blocked="!ready" @next="$emit('next')" @back="$emit('back')"/>
    </template>
  </SigningStepFrame>
</template>
