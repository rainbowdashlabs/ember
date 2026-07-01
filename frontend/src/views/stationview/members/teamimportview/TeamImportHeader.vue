/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="flex items-center justify-between">
    <SubHeader>{{ t('teamImport.title') }}</SubHeader>
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'members-import' })">
      {{ t('common.back') }}
    </SecondaryButton>
  </div>
</template>
