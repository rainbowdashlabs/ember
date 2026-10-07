/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {RequirementStatus, type RequiredDocumentStatus} from '@/api/generated/schema'
import {formatDate} from '@/util/format'

/** Where a participant's copy of a document stands: generated on a day, outdated, or not yet generated. */
const props = defineProps<{
  document: RequiredDocumentStatus
}>()

const {t} = useI18n()

const generated = computed(() => props.document.status === RequirementStatus.GENERATED)
</script>

<template>
  <InfoBadge v-if="generated && document.outdated">{{ t('events.documents.outdated') }}</InfoBadge>
  <SuccessBadge v-else-if="generated">
    {{ t('events.documents.generated', {date: formatDate(document.generatedAt)}) }}
  </SuccessBadge>
  <SecondaryBadge v-else>{{ t('events.documents.notGenerated') }}</SecondaryBadge>
</template>
