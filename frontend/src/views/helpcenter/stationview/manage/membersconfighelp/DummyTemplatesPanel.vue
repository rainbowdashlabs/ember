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

const templates = [
  {icon: 'house', label: 'Adresse'},
  {icon: 'calendar-plus', label: 'Geburtsdatum'},
  {icon: 'phone', label: 'Festnetz'},
  {icon: 'mobile-screen', label: 'Mobilnummer'},
  {icon: 'triangle-exclamation', label: 'Notfallkontakt'},
  {icon: 'id-card', label: 'Führerschein'},
  {icon: 'calendar-plus', label: 'Beitrittsdatum'},
  {icon: 'hashtag', label: 'Personalnummer'},
  {icon: 'rainbow', label: 'Geschlecht'},
  {icon: 'fire', label: 'Jugendflamme'},
  {icon: 'medal', label: 'Leistungsspange'},
]
</script>

<template>
  <NeutralContainer class="space-y-3">
    <FieldLabel hint>{{ t('membersConfig.templates') }}</FieldLabel>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton v-for="template in templates" :key="template.label" :icon="['fas', template.icon]" disabled>
        {{ template.label }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
