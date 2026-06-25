/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <FieldLabel hint>{{ t('membersConfig.templates') }}</FieldLabel>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton :icon="['fas', 'house']" disabled>Adresse</SecondaryButton>
      <SecondaryButton :icon="['fas', 'calendar-plus']" disabled>Geburtsdatum</SecondaryButton>
      <SecondaryButton :icon="['fas', 'phone']" disabled>Festnetz</SecondaryButton>
      <SecondaryButton :icon="['fas', 'mobile-screen']" disabled>Mobilnummer</SecondaryButton>
      <SecondaryButton :icon="['fas', 'triangle-exclamation']" disabled>Notfallkontakt</SecondaryButton>
      <SecondaryButton :icon="['fas', 'id-card']" disabled>Führerschein</SecondaryButton>
      <SecondaryButton :icon="['fas', 'calendar-plus']" disabled>Beitrittsdatum</SecondaryButton>
      <SecondaryButton :icon="['fas', 'hashtag']" disabled>Personalnummer</SecondaryButton>
      <SecondaryButton :icon="['fas', 'rainbow']" disabled>Geschlecht</SecondaryButton>
      <SecondaryButton :icon="['fas', 'fire']" disabled>Jugendflamme</SecondaryButton>
      <SecondaryButton :icon="['fas', 'medal']" disabled>Leistungsspange</SecondaryButton>
    </div>
  </NeutralContainer>
</template>
