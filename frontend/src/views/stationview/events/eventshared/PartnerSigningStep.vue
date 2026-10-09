/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {PartnerDocumentToSign} from '@/api/generated/schema'
import SignatureFieldList from './SignatureFieldList.vue'
import SignatureStateBadge from './SignatureStateBadge.vue'
import {fieldsToSign} from './requirementSignatures'

/**
 * The step a registration for a partner station's appointment ends on, where that appointment asks for
 * documents to be signed: each document filed here for the people just registered, with its fields. A
 * field the reader may sign opens the signing screen; signing happens here, and the sealed copy goes to
 * the partner station afterwards. Closed, the signatures stay among the open tasks.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  documents: PartnerDocumentToSign[]
}>()

const {t} = useI18n()

const named = computed(() => new Set(props.documents.map(document => document.memberId)).size > 1)
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-3" data-testid="partner-signing-step">
      <SubHeader>{{ t('events.documents.partnerStep.title') }}</SubHeader>
      <MutedText size="sm" tag="p">{{ t('events.documents.partnerStep.hint') }}</MutedText>
      <NeutralContainer v-for="document in documents" :key="document.signature.requestUid" class="space-y-2"
                        data-testid="partner-signing-document">
        <div class="flex flex-wrap items-center gap-2">
          <FieldLabel class="flex-1">
            {{ named ? t('events.documents.step.copyFor', {document: document.name, name: document.memberName}) : document.name }}
          </FieldLabel>
          <SignatureStateBadge :state="document.signature.state"/>
        </div>
        <SignatureFieldList :signature="document.signature" offer-signing/>
        <MutedText v-if="fieldsToSign(document.signature).length === 0" size="sm" tag="p">
          {{ t('events.documents.step.othersAsked') }}
        </MutedText>
      </NeutralContainer>
      <div class="flex justify-end">
        <SecondaryButton data-testid="partner-signing-later" @click="open = false">
          {{ t('events.documents.step.later') }}
        </SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
