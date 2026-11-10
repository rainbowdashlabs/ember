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
import type {StepDocumentView} from './stepDocumentView'

/**
 * The document a document step asks to sign: its name, where its signatures stand, and the ways on from
 * there the reader has: opening it, signing it, or asking for it again.
 */
const {t} = useI18n()

defineProps<{
  item: ProcedureItem
  dependencyMet: boolean
  /** The document and the reader's ways on from it. */
  document: StepDocumentView
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
      <span v-if="document.name" class="font-medium">{{ document.name }}</span>
      <span data-testid="procedure-step-document-state">{{ documentLine(item, document.state, dependencyMet) }}</span>
    </div>
    <div v-if="document.href || document.signHref || document.canAskAgain" class="flex flex-wrap items-center gap-3">
      <AppLink v-if="document.href" :href="document.href" :icon="['fas', 'file-lines']"
               data-testid="procedure-step-document-open">
        {{ t('procedures.document.open') }}
      </AppLink>
      <AppLink v-if="document.signHref" :href="document.signHref" :icon="['fas', 'signature']"
               data-testid="procedure-step-document-sign">
        {{ document.signLabel }}
      </AppLink>
      <SecondaryButton v-if="document.canAskAgain" :icon="['fas', 'rotate']" data-testid="procedure-step-ask-again"
                       @click="emit('ask-again')">
        {{ t('procedures.document.askAgain') }}
      </SecondaryButton>
    </div>
  </div>
</template>
