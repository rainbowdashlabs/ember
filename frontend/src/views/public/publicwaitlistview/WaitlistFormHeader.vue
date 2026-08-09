/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  listName: string
  listDescription?: string | null
  showBack: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center justify-between">
      <SubHeader>{{ listName }}</SubHeader>
      <SecondaryButton v-if="showBack" compact @click="emit('back')">
        {{ t('waitingList.back') }}
      </SecondaryButton>
    </div>
    <p v-if="listDescription" class="text-sm text-(--text-muted)">{{ listDescription }}</p>
  </NeutralContainer>
</template>
