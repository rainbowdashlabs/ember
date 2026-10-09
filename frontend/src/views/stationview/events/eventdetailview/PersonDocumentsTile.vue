/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {formatDateTime} from '@/util/format'

/**
 * One person's tile of documents to bring: their name, for a member of a partner station their station,
 * and below it each of their documents ({@link PersonDocument}, or {@link PartnerDocument} for a partner's
 * member). Members see their own and their children's tiles; a manager of the registrations sees one
 * per participant. Where a registration carries a withdrawn agreement, the tile says when.
 */
defineProps<{
  name: string
  /** The partner station that registered the person, or nothing for the station's own. */
  station?: string | null
  /** When a signed agreement was withdrawn while the registration stood, until it is signed anew. */
  withdrawnAt?: string | null
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="person-documents">
    <div>
      <p class="font-semibold">{{ name }}</p>
      <MutedText v-if="station" size="sm" tag="p" data-testid="person-documents-station">
        {{ t('events.documents.review.station', {station}) }}
      </MutedText>
    </div>
    <ErrorBadge v-if="withdrawnAt" data-testid="person-documents-withdrawn">
      {{ t('events.documents.review.withdrawnAt', {date: formatDateTime(withdrawnAt)}) }}
    </ErrorBadge>
    <slot/>
  </NeutralContainer>
</template>
