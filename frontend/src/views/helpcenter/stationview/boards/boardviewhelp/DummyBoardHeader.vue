/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
    <div class="flex items-center gap-3">
      <SectionHeader>Dienstplanung</SectionHeader>
      <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">PLAN</span>
    </div>
    <div class="flex items-center gap-2">
      <TextInput :model-value="''" :placeholder="t('boards.searchTickets')" class="w-48 md:w-96" />
      <PrimaryButton>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('boards.createTicket') }}
      </PrimaryButton>
      <IconButton :icon="['fas', 'gears']" :label="t('boards.settings')" />
    </div>
  </div>
</template>
