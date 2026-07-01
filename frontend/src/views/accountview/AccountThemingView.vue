/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import ThemeSection from '@/views/stationview/profile/settingsview/ThemeSection.vue'

const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.account-theming.title')" :subtitle="t('pages.account-theming.subtitle')">
    <ThemeSection />
  </ViewContent>
</template>
