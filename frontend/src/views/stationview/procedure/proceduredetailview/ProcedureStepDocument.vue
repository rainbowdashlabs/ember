/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import type {ProcedureItem, StepDocumentState} from '@/api/generated/schema'

/**
 * The document a document step asks to sign: its name, where its signatures stand, and the ways on from
 * there the reader has: opening it, signing it, or asking for it again.
 */
const {t} = useI18n()

defineProps<{
  item: ProcedureItem
  dependencyMet: boolean
  /** The name of the document the step asks to sign. */
  documentName?: string
  /** Where the document it made stands with its signatures, or nothing where it made none yet. */
  documentState?: StepDocumentState | null
  /** Where the reader opens the document, or nothing where they may not. */
  documentHref?: string | null
  /** Where the reader has it signed in front of them, or signs it themselves, or nothing. */
  signHref?: string | null
  /** What the sign link says: signing in front of a witness, or signing oneself. */
  signLabel?: string
  /** Whether the reader may ask for the document again. */
  canAskAgain?: boolean
}>()

const emit = defineEmits<{
  'ask-again': []
}>()

/** Where the document of a document step stands, in words. */
function documentLine(item: ProcedureItem, state: StepDocumentState | null | undefined, met: boolean): string {
  if (!state) {
    if (item.checked) return t('procedures.document.done')
    return met ? t('procedures.document.notMade') : t('procedures.document.later')
  }
  switch (state.progress) {
    case 'NOT_ASKED':
      return t('procedures.document.notAsked')
    case 'WAITING':
      return t('procedures.document.waiting', {settled: state.settled, fields: state.fields})
    case 'AGREED':
      return t('procedures.document.agreed')
    case 'NOT_AGREED':
      return t('procedures.document.notAgreed')
    case 'WITHDRAWN':
      return t('procedures.document.withdrawn')
  }
}
</script>

<template>
  <div class="mt-1 space-y-1 text-sm" data-testid="procedure-step-document">
    <div class="flex flex-wrap items-center gap-x-2 text-[var(--text-muted)]">
      <font-awesome-icon :icon="['fas', 'file-signature']" class="w-3 h-3"/>
      <span v-if="documentName" class="font-medium">{{ documentName }}</span>
      <span data-testid="procedure-step-document-state">{{ documentLine(item, documentState, dependencyMet) }}</span>
    </div>
    <div v-if="documentHref || signHref || canAskAgain" class="flex flex-wrap items-center gap-3">
      <AppLink v-if="documentHref" :href="documentHref" :icon="['fas', 'file-lines']"
               data-testid="procedure-step-document-open">
        {{ t('procedures.document.open') }}
      </AppLink>
      <AppLink v-if="signHref" :href="signHref" :icon="['fas', 'signature']"
               data-testid="procedure-step-document-sign">
        {{ signLabel }}
      </AppLink>
      <SecondaryButton v-if="canAskAgain" :icon="['fas', 'rotate']" data-testid="procedure-step-ask-again"
                       @click="emit('ask-again')">
        {{ t('procedures.document.askAgain') }}
      </SecondaryButton>
    </div>
  </div>
</template>
