/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <div>
    <SectionHeader>
      <font-awesome-icon :icon="['fas', 'umbrella-beach']" class="mr-2"/>
      {{ t('events.breaks') }}
    </SectionHeader>
    <div class="space-y-2">
      <NeutralContainer class="flex items-center justify-between">
        <div>
          <span class="font-medium">Sommerferien</span>
          <MutedText class="ml-2">01.07.2026 – 15.08.2026</MutedText>
        </div>
        <div class="flex items-center gap-1">
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
    </div>
    <div class="flex gap-2 mt-3">
      <PrimaryButton :icon="['fas', 'plus']">
        {{ t('events.addBreak') }}
      </PrimaryButton>
      <SecondaryButton :icon="['fas', 'download']">
        {{ t('events.importHolidays') }}
      </SecondaryButton>
    </div>
  </div>
</template>
