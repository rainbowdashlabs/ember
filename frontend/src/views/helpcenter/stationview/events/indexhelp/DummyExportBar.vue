/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex items-center justify-between mt-3">
    <SectionHeader>{{ t('events.export') }}</SectionHeader>
    <PrimaryButton :icon="['fas', 'file-export']">
      {{ t('events.exportPdf') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
