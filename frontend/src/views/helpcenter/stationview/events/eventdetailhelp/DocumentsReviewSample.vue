/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import FoldButton from '@/views/stationview/events/eventdetailview/FoldButton.vue'
import PersonDocumentsTile from '@/views/stationview/events/eventdetailview/PersonDocumentsTile.vue'

/**
 * A still picture of one document's "Dokumente der Teilnehmenden" for the help article: the group that
 * needs the manager open with a waiting scan and a partner's member whose signature is missing, the other
 * two groups folded with their counts.
 */
const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>
      <FoldButton :model-value="true">
        {{ t('events.documents.review.title', {name: t('helpCenter.eventDetail.documentsReviewSample')}) }}
      </FoldButton>
    </SubHeader>
    <FoldButton :model-value="true">
      <span class="font-medium">{{ t('events.documents.review.group.todo', {count: 2}) }}</span>
    </FoldButton>
    <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
      <PersonDocumentsTile :name="t('helpCenter.sample.people.lisaSchmidt')">
        <ButtonRow>
          <SecondaryButton compact :icon="['fas', 'download']" disabled>
            {{ t('events.documents.review.scanDownload') }}
          </SecondaryButton>
          <SuccessButton compact :icon="['fas', 'check']" disabled>{{ t('events.documents.scanConfirm') }}</SuccessButton>
          <ErrorButton compact :icon="['fas', 'xmark']" disabled>{{ t('events.documents.scanReject') }}</ErrorButton>
        </ButtonRow>
        <InfoBadge>{{ t('events.documents.scanWaiting') }}</InfoBadge>
      </PersonDocumentsTile>
      <PersonDocumentsTile :name="t('helpCenter.sample.people.tomMueller')" :station="t('helpCenter.sample.stations.north')">
        <ErrorBadge>{{ t('events.documents.partners.missing') }}</ErrorBadge>
        <ButtonRow>
          <SecondaryButton compact :icon="['fas', 'check']" disabled>
            {{ t('events.documents.partners.confirmPaper') }}
          </SecondaryButton>
        </ButtonRow>
      </PersonDocumentsTile>
    </div>
    <FoldButton :model-value="false">
      <span class="font-medium">{{ t('events.documents.review.group.missing', {count: 3}) }}</span>
    </FoldButton>
    <FoldButton :model-value="false">
      <span class="font-medium">{{ t('events.documents.review.group.done', {count: 12}) }}</span>
    </FoldButton>
  </NeutralContainer>
</template>
