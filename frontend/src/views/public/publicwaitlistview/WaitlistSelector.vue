/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {PublicWaitlistSummary} from '@/api/types'

defineProps<{
  lists: PublicWaitlistSummary[]
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('waitingList.publicRegistration.selectList') }}</SubHeader>
    <NeutralContainer
        v-for="l in lists"
        :key="l.id"
        class="cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all p-4"
        @click="emit('select', l.id)"
    >
      <SubHeader>{{ l.name }}</SubHeader>
      <p v-if="l.description" class="text-sm text-(--text-muted) mt-1">{{ l.description }}</p>
    </NeutralContainer>
  </div>
</template>
