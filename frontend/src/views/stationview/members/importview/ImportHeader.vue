/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="flex items-center justify-between">
    <SectionHeader>{{ t('memberImport.title') }}</SectionHeader>
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'members-create' })">
      {{ t('common.back') }}
    </SecondaryButton>
  </div>
</template>
