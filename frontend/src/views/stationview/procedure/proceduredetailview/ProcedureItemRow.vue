/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ProcedureStepDocument from './ProcedureStepDocument.vue'
import ProcedureStepMark from './ProcedureStepMark.vue'
import type {ProcedureItem, StepDocumentState} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()

defineProps<{
  item: ProcedureItem
  canEdit: boolean
  canCheck: boolean
  dependencyMet: boolean
  dependencyNames: string[]
  /** The name of the document a document step asks to sign. */
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
  toggle: []
  'update-note': [note: string]
  'ask-again': []
}>()

function onNoteBlur(e: Event) {
  emit('update-note', (e.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <component :is="item.checked ? SuccessContainer : NeutralContainer">
    <div class="flex items-start gap-3">
      <ProcedureStepMark :item="item" :can-check="canCheck" :dependency-met="dependencyMet"
                         :dependency-names="dependencyNames" @toggle="emit('toggle')"/>
      <div class="flex-1 min-w-0">
        <div class="font-medium" :class="{ 'line-through opacity-60': item.checked }">{{ item.title }}</div>
        <div v-if="item.description" class="text-sm text-[var(--text-muted)]">{{ item.description }}</div>
        <ProcedureStepDocument
            v-if="item.kind === 'DOCUMENT'"
            :item="item"
            :dependency-met="dependencyMet"
            :document-name="documentName"
            :document-state="documentState"
            :document-href="documentHref"
            :sign-href="signHref"
            :sign-label="signLabel"
            :can-ask-again="canAskAgain"
            @ask-again="emit('ask-again')"
        />
        <div v-if="!dependencyMet && !item.checked"
             class="text-xs text-[var(--text-muted)] mt-1 flex items-center gap-1">
          <font-awesome-icon :icon="['fas', 'lock']" class="w-3 h-3"/>
          {{ t('procedures.dependsOn') }}: {{ dependencyNames.join(', ') }}
        </div>
        <div v-if="item.checkedAt" class="text-xs text-[var(--text-muted)] mt-1">
          {{ t('procedures.checkedBy') }}: {{ formatDateTime(item.checkedAt) }}
        </div>
        <div v-if="item.note || (!item.checked && canEdit)" class="mt-2">
          <TextAreaInput
              v-if="!item.checked && canEdit"
              :model-value="item.note ?? ''"
              :placeholder="t('procedures.itemNote')"
              class="!text-xs !py-1 !min-h-0"
              :rows="1"
              @blur="onNoteBlur"
          />
          <p v-else-if="item.note" class="text-sm text-[var(--text-muted)] italic">{{ item.note }}</p>
        </div>
      </div>
    </div>
  </component>
</template>
