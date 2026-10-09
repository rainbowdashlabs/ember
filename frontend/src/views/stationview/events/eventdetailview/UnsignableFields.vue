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
import MutedText from '@/components/typography/MutedText.vue'
import {signing} from '@/api'
import {FieldSettlement} from '@/api/signing'
import {RequirementSignatureState, StationPermission} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'
import {signerLabel} from '../eventshared/requirementSignatures'
import type {ParticipantCopy} from './documentTiles'

/**
 * The open fields of a participant's copy that nobody can sign, such as a guardian place nobody holds, for
 * a manager of the registrations. Whoever may change member documents confirms one as signed on paper or
 * waives it, asked once more before it is done, since a settled field stays settled. Anybody else is told
 * who can.
 */
const props = defineProps<{
  copy: ParticipantCopy
  /** Called once a field was settled, so the documents are read again. */
  onChanged: () => void
}>()

/** A field the manager chose to settle, waiting for the second press. */
interface Asked {
  fieldName: string
  settlement: FieldSettlement
}

const SETTLEMENTS: readonly FieldSettlement[] = [FieldSettlement.PAPER, FieldSettlement.WAIVE]

const {t} = useI18n()
const {hasPermission} = useSession()

const settles = computed(() => hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER))
const fields = computed(() => props.copy.document.signature?.fields.filter(field =>
    field.nobodyCanSign && field.state === RequirementSignatureState.OPEN) ?? [])
const asked = ref<Asked | null>(null)

const settling = useAsyncAction(async () => {
  const requestUid = props.copy.document.signature?.requestUid
  const chosen = asked.value
  if (!requestUid || !chosen) return
  await signing.settleSignatureField(requestUid, chosen.fieldName, chosen.settlement)
  asked.value = null
  props.onChanged()
})
</script>

<template>
  <div v-if="fields.length > 0" class="space-y-2" data-testid="unsignable-fields">
    <FailureAlert :failure="settling.failure.value"/>
    <ul class="space-y-2">
      <li v-for="field in fields" :key="field.id" class="space-y-1" data-testid="unsignable-field">
        <p class="text-sm">{{ t('events.documents.review.nobodySigns', {signer: signerLabel(field, t)}) }}</p>
        <template v-if="asked?.fieldName === field.name">
          <MutedText size="sm" tag="p">{{ t(`signing.manage.confirm.${asked.settlement}`) }}</MutedText>
          <ButtonRow pair>
            <SecondaryButton compact @click="asked = null">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton compact :disabled="settling.running.value" data-testid="unsignable-field-confirm"
                           @click="settling.run()">
              {{ t('signing.manage.confirmYes') }}
            </PrimaryButton>
          </ButtonRow>
        </template>
        <ButtonRow v-else-if="settles">
          <SecondaryButton v-for="settlement in SETTLEMENTS" :key="settlement" compact
                           :data-testid="`unsignable-field-${settlement}`"
                           @click="asked = {fieldName: field.name, settlement}">
            {{ t(`signing.manage.action.${settlement}`) }}
          </SecondaryButton>
        </ButtonRow>
        <MutedText v-else size="sm" tag="p">{{ t('events.documents.review.nobodySignsRight') }}</MutedText>
      </li>
    </ul>
  </div>
</template>
