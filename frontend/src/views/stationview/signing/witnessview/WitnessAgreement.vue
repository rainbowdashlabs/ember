/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import SigningFillIns from '../signingview/SigningFillIns.vue'
import type {FillInResponse, WitnessFieldResponse} from '@/api/generated/schema'
import type {FillInValues} from '../signingview/fillIns'

/**
 * The field as the person in front of the witness agrees to it: where they sign, the statement as one box
 * to tick in their own name, and the details the document asks them to fill in.
 */
defineProps<{
  field: WitnessFieldResponse
  name: string
  fillIns: FillInResponse[]
}>()

const agreed = defineModel<boolean>('agreed', {required: true})
const values = defineModel<FillInValues>('values', {required: true})

const {t} = useI18n()
const boxId = useId()
</script>

<template>
  <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3" data-testid="witness-agreement">
    <MutedText tag="p" size="sm">{{ t('signing.flow.document.place', {field: t(`signing.ask.role.${field.role}`)}) }}</MutedText>
    <p class="whitespace-pre-line text-sm" data-testid="signing-statement">{{ field.statement }}</p>
    <SigningFillIns v-if="fillIns.length > 0" v-model="values" :fields="fillIns" :locked="false"/>
    <FieldLabel inline :for="boxId" class="cursor-pointer">
      <CheckboxInput :id="boxId" v-model="agreed" data-testid="witness-agree"/>
      {{ t('signing.witness.read.agree', {name}) }}
    </FieldLabel>
  </div>
</template>
