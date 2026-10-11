/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {documentTemplates} from '@/api'
import type {MemberDocumentResponse, SelfServiceOffer} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDate} from '@/util/format'
import {recentlyUsedFirst} from './recentlyUsedFirst'
import {missingLabels} from './generation'
import {showToast} from '@/util/toast'
import {stationDocumentSource} from '@/api/documents'
import type {MemberLike} from '@/components/input/select/memberOption'
import DocumentModal from './DocumentModal.vue'

/**
 * The documents a member may generate for themselves, or a guardian for the member in their care:
 * each offered template with what still stands in its way, the data the profile lacks or the day it
 * can be generated again. A generated document is filed with the member it is about and opens at once,
 * so it can be read and saved without looking for it. The templates generated most recently for this
 * member through self service come first.
 */
const props = defineProps<{
  memberId: number
  /** The people the reader sees documents of, which name whom a created document belongs to. */
  people?: MemberLike[]
}>()

const {t} = useI18n()
const created = ref<MemberDocumentResponse | null>(null)
const showCreated = ref(false)
const offers = ref<SelfServiceOffer[]>([])

const {loading, failure, reload} = useAsyncLoader(async () => {
  offers.value = recentlyUsedFirst(await documentTemplates.selfServiceOffers(props.memberId))
})

const generating = useAsyncAction(async (templateId: number) => {
  const generated = await documentTemplates.generateForSelf(props.memberId, templateId)
  await reload()
  const filed = await stationDocumentSource.listOf(props.memberId).catch(() => [])
  created.value = filed.find(document => document.id === generated.documentId) ?? null
  if (created.value) showCreated.value = true
  else showToast(t('documentTemplates.generated'), 'success')
})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="self-service-documents">
    <SubHeader>{{ t('documentTemplates.selfServiceCreate') }}</SubHeader>
    <FailureAlert :failure="failure ?? generating.failure.value"/>
    <Spinner v-if="loading" size="md"/>
    <EmptyState v-else-if="offers.length === 0" compact>{{ t('documentTemplates.selfServiceNone') }}</EmptyState>
    <div v-for="offer in offers" :key="offer.templateId" class="flex flex-wrap items-center justify-between gap-3"
         data-testid="self-service-offer">
      <div class="space-y-1">
        <span class="font-semibold">{{ offer.name }}</span>
        <MutedText v-if="offer.availableFrom" size="sm" tag="p">
          {{ t('documentTemplates.availableFrom', {date: formatDate(offer.availableFrom)}) }}
        </MutedText>
        <MutedText v-if="offer.missing.length > 0" size="sm" tag="p">
          {{ t('documentTemplates.selfServiceMissing', {values: missingLabels(offer.missing)}) }}
        </MutedText>
      </div>
      <SecondaryButton :icon="['fas', 'file-circle-plus']" :disabled="generating.running.value"
                       data-testid="self-service-generate" @click="generating.run(offer.templateId)">
        {{ t('common.create') }}
      </SecondaryButton>
    </div>
    <DocumentModal
        v-model="showCreated"
        :document="created"
        :all-members="props.people"
        :content-url="stationDocumentSource.contentUrl"
        :version-url="stationDocumentSource.versionUrl"
        :record-url="stationDocumentSource.recordUrl"
        data-testid="self-service-created"
    />
  </NeutralContainer>
</template>
