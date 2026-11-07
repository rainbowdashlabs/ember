/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

/**
 * A piece the member should hold and does not, standing among the pieces of its inventory where the
 * reader would look for it.
 */
defineProps<{
  inventoryName: string
  canHandOut: boolean
}>()

const emit = defineEmits<{handOut: []}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex items-center justify-between gap-2 border-dashed" data-testid="missing-requirement">
    <div class="space-y-1">
      <div class="text-sm font-medium">{{ inventoryName }}</div>
      <ErrorBadge>{{ t('memberDetail.missingBadge') }}</ErrorBadge>
    </div>
    <PrimaryButton v-if="canHandOut" :icon="['fas', 'plus']" @click="emit('handOut')">
      {{ t('memberDetail.handOut') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
