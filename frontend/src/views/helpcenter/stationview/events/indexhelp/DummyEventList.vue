/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <div>
    <SubHeader class="mt-3">Übung</SubHeader>
    <div class="space-y-2">
      <NeutralContainer class="flex items-center justify-between">
        <div>
          <span class="font-medium">Übungsabend</span>
          <MutedText class="ml-2">Dienstag, 18:00 – 20:00</MutedText>
          <span class="ml-2 text-xs text-primary">{{ t('events.typeRecurring') }}</span>
        </div>
        <div class="flex items-center gap-1">
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
      <NeutralContainer class="flex items-center justify-between">
        <div>
          <span class="font-medium">Wettkampf Vorbereitung</span>
          <MutedText class="ml-2">25.05.2026, 14:00 – 17:00</MutedText>
          <span class="ml-2 text-xs text-primary">{{ t('events.typeOneTime') }}</span>
        </div>
        <div class="flex items-center gap-1">
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
    </div>

    <div class="flex justify-end mt-3">
      <PrimaryButton :icon="['fas', 'plus']">
        {{ t('events.addEvent') }}
      </PrimaryButton>
    </div>
  </div>
</template>
