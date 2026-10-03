/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {RequirementStatus, type RequiredDocumentStatus} from '@/api/generated/schema'
import {formatDate} from '@/util/format'

/**
 * One document a participant is asked to bring: whether their copy was generated, and the button
 * that hands it over, generating it first where needed.
 */
const props = defineProps<{
  document: RequiredDocumentStatus
  busy: boolean
}>()

const emit = defineEmits<{
  fetch: []
}>()

const {t} = useI18n()

const generated = computed(() => props.document.status === RequirementStatus.GENERATED)
</script>

<template>
  <div class="flex flex-wrap items-center gap-2" data-testid="document-to-bring">
    <span class="flex-1">{{ document.name }}</span>
    <InfoBadge v-if="generated && document.outdated">{{ t('events.documents.outdated') }}</InfoBadge>
    <SuccessBadge v-else-if="generated">
      {{ t('events.documents.generated', {date: formatDate(document.generatedAt)}) }}
    </SuccessBadge>
    <SecondaryBadge v-else>{{ t('events.documents.notGenerated') }}</SecondaryBadge>
    <SecondaryButton :icon="['fas', 'download']" :disabled="busy" data-testid="document-to-bring-download"
                     @click="emit('fetch')">
      {{ t('events.documents.download') }}
    </SecondaryButton>
  </div>
</template>
