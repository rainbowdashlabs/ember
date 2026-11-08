/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {WitnessFieldResponse, WitnessPersonResponse} from '@/api/generated/schema'

/**
 * The open fields of the person's documents a witness may collect, one button each. A field nobody can
 * sign right now, such as a guardian place nobody holds, is shown but cannot be chosen.
 */
defineProps<{
  person: WitnessPersonResponse
  fields: WitnessFieldResponse[]
  busy: boolean
}>()

defineEmits<{choose: [field: WitnessFieldResponse]}>()

const {t} = useI18n()

function line(field: WitnessFieldResponse): string {
  return t('signing.witness.document.field', {
    document: field.documentTitle ?? t('signing.flow.untitled'),
    role: t(`signing.ask.role.${field.role}`),
  })
}
</script>

<template>
  <section class="space-y-2" data-testid="witness-open-fields">
    <SubHeader>{{ t('signing.witness.document.open') }}</SubHeader>
    <MutedText v-if="fields.length === 0" tag="p" size="sm">{{ t('signing.witness.document.noOpen', {name: person.name}) }}</MutedText>
    <ul class="space-y-2">
      <li v-for="field in fields" :key="field.fieldId" class="space-y-1">
        <SecondaryButton type="button" class="w-full justify-start" :disabled="busy || field.signers.length === 0"
                         :data-testid="`witness-field-${field.fieldId}`" @click="$emit('choose', field)">
          {{ line(field) }}
        </SecondaryButton>
        <MutedText v-if="field.signers.length === 0" tag="p" size="sm">{{ t('signing.witness.document.nobody') }}</MutedText>
      </li>
    </ul>
  </section>
</template>
