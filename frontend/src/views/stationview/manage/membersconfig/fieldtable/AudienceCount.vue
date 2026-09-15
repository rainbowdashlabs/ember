/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

/**
 * How many audiences a question is put to.
 *
 * <p>None is worth saying out loud: the question is written down and reaches nobody, which looks
 * exactly like a question that works until somebody goes looking for it on a profile.
 */
defineProps<{ count: number }>()

const {t} = useI18n()
</script>

<template>
  <ErrorBadge v-if="count === 0" data-testid="asked-of-nobody">
    {{ t('membersConfig.audiences.none') }}
  </ErrorBadge>
  <SecondaryBadge v-else>{{ count }}</SecondaryBadge>
</template>
