/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {FieldSettlement} from '@/api/signing'
import {FieldState, type ManagedFieldResponse} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'
import {signerOf} from '../signatureSigner'
import {proofWords} from './proofWords'

/**
 * One field of a request for signatures as its document's manager sees it: who is asked, how it stands,
 * what the signer confirms, and the act that signed it or who settled it otherwise.
 *
 * <p>A field that still waits offers what no signing act does: confirming a paper signature, waiving it or
 * withdrawing it. Each is asked once more before it is done, since a settled field stays settled. A field
 * nobody can sign says so, because only these actions settle it.
 */
const props = defineProps<{
  field: ManagedFieldResponse
  /** Whether the request still waits, so the field can be settled. */
  settleable: boolean
  /** Whether an action on the request is under way. */
  busy: boolean
}>()

const emit = defineEmits<{
  settle: [settlement: FieldSettlement]
}>()

const {t} = useI18n()

const asked = ref<FieldSettlement | null>(null)


const waits = computed(() => props.settleable && props.field.state === FieldState.OPEN)

/** The act in one sentence: who signed, through whose account where it was another's, when and how. */
const actLine = computed(() => {
  const act = props.field.act
  if (!act) return null
  const named = {
    signer: act.signerName,
    holder: act.accountHolderName,
    time: formatDateTime(act.signedAt),
    proof: proofWords(act.proof, t),
  }
  return t(act.signerName === act.accountHolderName ? 'signing.manage.act' : 'signing.manage.actThrough', named)
})

/** Who settled the field without a signing act, and when. */
const settledLine = computed(() => {
  const {state, settledByName, settledAt} = props.field
  if (state === FieldState.OPEN || state === FieldState.SIGNED || !settledByName) return null
  return t(`signing.manage.settled.${state}`, {name: settledByName, time: formatDateTime(settledAt)})
})

function settle() {
  if (!asked.value) return
  emit('settle', asked.value)
  asked.value = null
}
</script>

<template>
  <li class="space-y-1" data-testid="signature-field">
    <p class="text-sm font-medium">
      {{ t(`signing.ask.role.${props.field.role}`) }}: {{ signerOf(props.field, t) }}
      <span class="text-(--text-muted)">({{ t(`signing.ask.state.${props.field.state}`) }})</span>
    </p>
    <template v-if="props.field.nobodyCanSign">
      <ErrorBadge data-testid="signature-field-nobody">{{ t('signing.state.nobodyCanSign') }}</ErrorBadge>
      <MutedText size="sm" tag="p">{{ t('signing.manage.nobodyHint') }}</MutedText>
    </template>
    <MutedText size="sm" tag="p">„{{ props.field.statement }}“</MutedText>
    <MutedText v-if="actLine && props.field.act" size="sm" tag="p" data-testid="signature-field-act">
      {{ actLine }} {{ t(props.field.act.bound ? 'signing.manage.bound' : 'signing.manage.unbound') }}
      <template v-if="!props.field.act.sealed">{{ t('signing.manage.notSealed') }}</template>
    </MutedText>
    <MutedText v-if="settledLine" size="sm" tag="p">{{ settledLine }}</MutedText>
    <div v-if="waits && asked" class="space-y-2" data-testid="signature-field-confirm">
      <MutedText size="sm" tag="p">{{ t(`signing.manage.confirm.${asked}`) }}</MutedText>
      <ButtonRow pair>
        <SecondaryButton @click="asked = null">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="props.busy" data-testid="signature-field-confirm-yes" @click="settle">
          {{ t('signing.manage.confirmYes') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
    <ButtonRow v-else-if="waits">
      <SecondaryButton v-for="settlement in Object.values(FieldSettlement)" :key="settlement"
                       :disabled="props.busy" :data-testid="`signature-field-${settlement}`"
                       @click="asked = settlement">
        {{ t(`signing.manage.action.${settlement}`) }}
      </SecondaryButton>
    </ButtonRow>
  </li>
</template>
