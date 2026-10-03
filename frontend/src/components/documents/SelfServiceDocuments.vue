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
import {documentTemplates} from '@/api'
import type {SelfServiceOffer} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDate} from '@/util/format'
import {recentlyUsedFirst} from './recentlyUsedFirst'
import {showToast} from '@/util/toast'

/**
 * The documents a member may generate for themselves, or a guardian for the member in their care:
 * each offered template with what still stands in its way, the data the profile lacks or the day it
 * can be generated again. A generated document is filed with the member it is about. The templates
 * generated most recently for this member through self service come first.
 */
const props = defineProps<{
  memberId: number
}>()

const emit = defineEmits<{
  filed: []
}>()

const {t} = useI18n()
const offers = ref<SelfServiceOffer[]>([])

const {loading, failure, reload} = useAsyncLoader(async () => {
  offers.value = recentlyUsedFirst(await documentTemplates.selfServiceOffers(props.memberId))
})

const generating = useAsyncAction(async (templateId: number) => {
  await documentTemplates.generateForSelf(props.memberId, templateId)
  showToast(t('documentTemplates.generated'), 'success')
  emit('filed')
  await reload()
})
</script>

<template>
  <NeutralContainer v-if="!loading && offers.length > 0" class="space-y-3" data-testid="self-service-documents">
    <SubHeader>{{ t('documentTemplates.selfServiceCreate') }}</SubHeader>
    <FailureAlert :failure="failure ?? generating.failure.value"/>
    <div v-for="offer in offers" :key="offer.templateId" class="flex flex-wrap items-center justify-between gap-3"
         data-testid="self-service-offer">
      <div class="space-y-1">
        <span class="font-semibold">{{ offer.name }}</span>
        <MutedText v-if="offer.availableFrom" size="sm" tag="p">
          {{ t('documentTemplates.availableFrom', {date: formatDate(offer.availableFrom)}) }}
        </MutedText>
        <MutedText v-if="offer.missing.length > 0" size="sm" tag="p">
          {{ t('documentTemplates.selfServiceMissing', {values: offer.missing.map(value => value.label).join(', ')}) }}
        </MutedText>
      </div>
      <SecondaryButton :icon="['fas', 'file-circle-plus']" :disabled="generating.running.value"
                       data-testid="self-service-generate" @click="generating.run(offer.templateId)">
        {{ t('documentTemplates.generate') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
