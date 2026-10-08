/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {LinkStatus} from '@/api/generated/schema'

/**
 * How the request to link a member's account stands, in a word: waiting for the person, declined by
 * them, or run out. The member list and the member's page both show it.
 */
defineProps<{
  status: LinkStatus
}>()

const {t} = useI18n()
</script>

<template>
  <InfoBadge v-if="status === LinkStatus.WAITING" class="text-[10px]" data-testid="member-link-waiting">
    {{ t('memberLinks.badge.WAITING') }}
  </InfoBadge>
  <ErrorBadge v-else-if="status !== LinkStatus.ACCEPTED" class="text-[10px]" :data-testid="`member-link-${status.toLowerCase()}`">
    {{ t(`memberLinks.badge.${status}`) }}
  </ErrorBadge>
</template>
