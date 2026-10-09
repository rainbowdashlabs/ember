/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import SigningFillIns from './SigningFillIns.vue'
import {SignerCapacity} from '@/api/generated/schema'
import type {FillInResponse, OpenSignatureResponse} from '@/api/generated/schema'
import type {FillInValues} from './fillIns'
import {signerSentence} from './batchFlow'

/**
 * One field of a document as its signer agrees to it: who signs and where, the statement as one box to
 * tick, and the details the document asks this signer to fill in, one per line.
 *
 * <p>A member signing through the reader's account is the one who reads and ticks here, so the box speaks
 * of them.
 */
const props = defineProps<{
  field: OpenSignatureResponse
  fillIns: FillInResponse[]
}>()

const agreed = defineModel<boolean>('agreed', {required: true})
const values = defineModel<FillInValues>('values', {required: true})

const {t} = useI18n()
const boxId = useId()

const place = computed(() => t('signing.flow.document.place', {field: t(`signing.ask.role.${props.field.role}`)}))

const agreeLabel = computed(() => props.field.capacity === SignerCapacity.MEMBER_THROUGH_ACCOUNT
    ? t('signing.flow.document.agreeThrough', {name: props.field.signerName ?? props.field.memberName})
    : t('signing.flow.document.agree'))
</script>

<template>
  <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3" data-testid="signing-field">
    <p class="font-medium" data-testid="signing-field-who">{{ signerSentence(field, t) }}</p>
    <MutedText tag="p" size="sm">{{ place }}</MutedText>
    <p class="whitespace-pre-line text-sm" data-testid="signing-statement">{{ field.statement }}</p>
    <SigningFillIns v-if="fillIns.length > 0" v-model="values" :fields="fillIns" :locked="false"/>
    <FieldLabel inline :for="boxId" class="cursor-pointer">
      <CheckboxInput :id="boxId" v-model="agreed" :data-testid="`signing-agree-${field.fieldId}`"/>
      {{ agreeLabel }}
    </FieldLabel>
  </div>
</template>
