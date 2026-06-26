/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  category: string
  name: string
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>
        <span class="text-xs uppercase tracking-wide text-(--text-muted) mr-2">{{ category }}</span>
        {{ name }}
      </SubHeader>
      <SecondaryButton :icon="['fas', 'plus']">
        {{ t('inventory.requirements.addItem') }}
      </SecondaryButton>
    </div>
    <slot/>
  </NeutralContainer>
</template>
