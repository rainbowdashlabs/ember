/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {SignerCapacity} from '@/api/generated/schema'
import type {OpenSignatureResponse} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'

/**
 * Who signs, in which capacity, and the statement they make, with the box that says they make it.
 *
 * <p>A member signing through somebody else's account is the one who reads and ticks; the holder of the
 * account only confirms afterwards. The wording follows that, so the person at the device is addressed.
 */
const props = defineProps<{
  field: OpenSignatureResponse
  /** Whether the act is being started, which is what the button waits for. */
  preparing: boolean
  /** Why the act could not be started, where it could not. */
  failure: Failure | null
  /** Whether the act was started, after which the button has done its part. */
  started: boolean
  /** Whether the document could be opened, without which nothing is signed. */
  documentReady: boolean
}>()

const confirmed = defineModel<boolean>('confirmed', {default: false})

defineEmits<{proceed: []}>()

const {t} = useI18n()
const headingId = useId()
const statementId = useId()
const boxId = useId()

/** The person whose signature this is, which is the member where the field names one. */
const signer = computed(() => props.field.signerName ?? props.field.memberName)

const capacityText = computed(() => {
  switch (props.field.capacity) {
    case SignerCapacity.GUARDIAN: return t('signing.statement.asGuardian', {name: props.field.memberName})
    case SignerCapacity.MEMBER_THROUGH_ACCOUNT: return t('signing.statement.throughAccount', {name: signer.value})
    default: return t('signing.statement.forYourself')
  }
})

const confirmLabel = computed(() => props.field.capacity === SignerCapacity.MEMBER_THROUGH_ACCOUNT
    ? t('signing.statement.confirmThrough', {name: signer.value})
    : t('signing.statement.confirm'))
</script>

<template>
  <section :aria-labelledby="headingId" class="space-y-3">
    <SectionHeader :id="headingId">{{ t('signing.statement.heading') }}</SectionHeader>
    <p class="text-sm" data-testid="signing-capacity">{{ capacityText }}</p>
    <blockquote
        :id="statementId"
        class="border-l-4 border-primary bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 rounded-r-theme px-4 py-3 whitespace-pre-line"
        data-testid="signing-statement"
    >{{ field.statement }}</blockquote>
    <FieldLabel inline :for="boxId" class="cursor-pointer">
      <CheckboxInput :id="boxId" v-model="confirmed" :aria-describedby="statementId"/>
      {{ confirmLabel }}
    </FieldLabel>
    <FailureAlert :failure="failure"/>
    <PrimaryButton
        v-if="!started"
        type="button"
        :disabled="!confirmed || !documentReady || preparing"
        data-testid="signing-proceed"
        @click="$emit('proceed')"
    >
      {{ preparing ? t('common.loading') : t('signing.statement.proceed') }}
    </PrimaryButton>
  </section>
</template>
