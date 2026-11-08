/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type { Failure } from '@/util/failure'

/**
 * Where the member page stands before its tabs: still loading, refused, or open to a reader who may
 * see none of its tabs, which is said rather than left as an empty page.
 */
defineProps<{
  nothingVisible: boolean
  loading: boolean
  failure: Failure | null
}>()

const { t } = useI18n()
</script>

<template>
  <Alert v-if="nothingVisible" variant="info">{{ t('memberDetail.nothingToShow') }}</Alert>
  <Spinner v-if="loading" size="lg" />
  <FailureAlert :failure="failure"/>
</template>
