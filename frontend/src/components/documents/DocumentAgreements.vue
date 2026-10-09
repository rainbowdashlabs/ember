/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {signing} from '@/api'
import type {DocumentAgreement} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'
import WithdrawAgreementModal from './WithdrawAgreementModal.vue'

/**
 * The agreements signed on a sealed document that the reader signed or acts for: one the reader may still
 * withdraw offers that, and a withdrawn one says when and by whom. Nothing shows for anybody else, and
 * nothing where the agreements cannot be read, since the document itself stands either way.
 */
const props = defineProps<{
  documentId: number
}>()

const {t} = useI18n()

const agreements = ref<DocumentAgreement[]>([])
const withdrawing = ref<string | null>(null)
const open = ref(false)

async function load() {
  agreements.value = await signing.documentAgreements(props.documentId).catch(() => [])
}

function startWithdrawing(agreement: DocumentAgreement) {
  withdrawing.value = agreement.requestUid
  open.value = true
}

watch(() => props.documentId, load, {immediate: true})
</script>

<template>
  <div v-if="agreements.length > 0" class="space-y-1" data-testid="document-agreements">
    <div v-for="agreement in agreements" :key="agreement.requestUid" class="flex flex-wrap items-center gap-2">
      <MutedText v-if="agreement.withdrawnAt" size="sm" data-testid="document-agreement-withdrawn">
        {{ t('documents.agreementWithdrawn', {date: formatDateTime(agreement.withdrawnAt), name: agreement.withdrawnBy}) }}
      </MutedText>
      <SecondaryButton v-if="agreement.withdrawable" :icon="['fas', 'rotate-left']"
                       data-testid="document-agreement-withdraw" @click="startWithdrawing(agreement)">
        {{ t('events.documents.withdraw.action') }}
      </SecondaryButton>
    </div>
    <WithdrawAgreementModal v-if="withdrawing" v-model="open" :request-uid="withdrawing" @withdrawn="load"/>
  </div>
</template>
