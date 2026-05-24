/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EmberLogo from '@/components/display/EmberLogo.vue'
import PageHeader from '@/components/typography/PageHeader.vue'

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-20 px-4 text-center">
    <EmberLogo base="NoBG_NoGlow_FAQ" blink-base="NoBG_NoGlow_FAQ_Blink" :pixel-size="512" size="w-32 h-32 mb-6" :blink="true" />
    <PageHeader class="text-6xl font-extrabold text-primary mb-2">404</PageHeader>
    <p class="text-xl font-medium mb-2">{{ t('notFound.title') }}</p>
    <p class="text-(--text-muted) mb-8 max-w-md">{{ t('notFound.text') }}</p>
    <div class="flex gap-3">
      <router-link to="/">
        <SecondaryButton :icon="['fas', 'house']">
          {{ t('notFound.home') }}
        </SecondaryButton>
      </router-link>
      <router-link to="/login">
        <SecondaryButton :icon="['fas', 'right-from-bracket']">
          {{ t('notFound.login') }}
        </SecondaryButton>
      </router-link>
    </div>
  </div>
</template>
