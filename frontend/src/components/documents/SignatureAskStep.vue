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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {signing} from '@/api'
import {
    FieldRole,
    SignerCapacity,
    type AskedFieldResponse,
    type SignatureAskResponse,
} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {showToast} from '@/util/toast'

/**
 * Asking for the signatures of a generated document: who would be asked to sign each field and what they
 * confirm, then one button that asks them all. Generating never asks on its own; this step follows right
 * after a manager generated a document and opens again from the list of generated documents. Where the
 * document was asked to be signed before, it shows how each field stands instead.
 *
 * <p>Right after generating, a document without a field to sign needs no step, so it ends at once where
 * asked to; from the list it says so.
 */
const props = withDefaults(defineProps<{
  /** The document's entry in the generation log. */
  generationId: number
  /** Whether a document without a field to sign ends the step at once rather than saying so. */
  skipWhenNone?: boolean
}>(), {
  skipWhenNone: false,
})

const emit = defineEmits<{
  done: []
}>()

const {t} = useI18n()

const ask = ref<SignatureAskResponse | null>(null)

const loader = useAsyncLoader(async isCurrent => {
  const loaded = await signing.getSignatureAsk(props.generationId)
  if (!isCurrent()) return
  ask.value = loaded
  if (props.skipWhenNone && loaded.request === null && loaded.fields.length === 0) emit('done')
})

const asking = useAsyncAction(async () => {
  ask.value = await signing.requestSignatures(props.generationId)
  showToast(t('signing.ask.asked'), 'success')
  emit('done')
})

const request = computed(() => ask.value?.request ?? null)
const fields = computed(() => ask.value?.fields ?? [])

function signerOf(field: AskedFieldResponse): string {
  if (field.role === FieldRole.ANY_GUARDIAN) return t('signing.ask.anyGuardian')
  if (!field.signerName) return t('signing.ask.nobody')
  if (field.capacity === SignerCapacity.MEMBER_THROUGH_ACCOUNT) {
    return t('signing.ask.throughAccount', {name: field.signerName})
  }
  return field.signerName
}

const keeping = computed(() => {
  const months = request.value?.retentionMonths
  if (months === null || months === undefined) return t('signing.ask.keptWhileMember')
  return t('signing.ask.keptMonths', {months})
})
</script>

<template>
  <div class="space-y-4" data-testid="signature-ask">
    <SubHeader>{{ t('signing.ask.heading') }}</SubHeader>
    <Spinner v-if="loader.loading.value"/>
    <FailureAlert :failure="loader.failure.value ?? asking.failure.value"/>
    <template v-if="ask">
      <MutedText v-if="fields.length === 0" size="sm" tag="p" data-testid="signature-ask-none">
        {{ t('signing.ask.none') }}
      </MutedText>
      <template v-else>
        <MutedText size="sm" tag="p">
          {{ request ? t(`signing.ask.requestState.${request.state}`) : t('signing.ask.intro') }}
        </MutedText>
        <ul class="space-y-3">
          <li v-for="field in fields" :key="field.fieldName" class="space-y-1" data-testid="signature-ask-field">
            <p class="text-sm font-medium">
              {{ t(`signing.ask.role.${field.role}`) }}: {{ signerOf(field) }}
              <span v-if="field.state" class="text-(--text-muted)">({{ t(`signing.ask.state.${field.state}`) }})</span>
            </p>
            <MutedText size="sm" tag="p">„{{ field.statement }}“</MutedText>
          </li>
        </ul>
        <MutedText v-if="request" size="sm" tag="p">
          {{ keeping }} {{ request.copyAttached ? t('signing.ask.copyAttached') : t('signing.ask.copyLinked') }}
        </MutedText>
        <MutedText v-else size="sm" tag="p">{{ t('signing.ask.later') }}</MutedText>
      </template>
    </template>
    <ButtonRow pair align="end">
      <SecondaryButton data-testid="signature-ask-skip" @click="emit('done')">
        {{ request || fields.length === 0 ? t('common.close') : t('signing.ask.skip') }}
      </SecondaryButton>
      <PrimaryButton v-if="!request && fields.length > 0" :icon="['fas', 'file-signature']"
                     :disabled="asking.running.value" data-testid="signature-ask-request" @click="asking.run()">
        {{ t('signing.ask.request') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
</template>
