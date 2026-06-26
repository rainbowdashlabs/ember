/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <div>
      <SectionHeader>Max Mustermann</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('inventory.check.title') }}</p>
    </div>
    <div class="flex gap-2">
      <PrimaryButton :icon="['fas', 'list-check']">
        {{ t('inventory.check.rapidCheck') }}
      </PrimaryButton>
      <SecondaryButton>{{ t('inventory.check.markAll') }}</SecondaryButton>
      <SecondaryButton>{{ t('inventory.check.cancel') }}</SecondaryButton>
    </div>
  </div>
</template>
