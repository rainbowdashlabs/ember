/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <SubHeader class="text-sm">{{ t('forms.restrictions.title') }}</SubHeader>
      <div class="space-y-2">
        <FieldHint>{{ t('forms.restrictions.roles') }}</FieldHint>
        <div class="flex flex-wrap gap-2">
          <span class="px-2 py-1 text-xs rounded border border-primary bg-primary/15 text-primary">Mitglied</span>
          <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Team</span>
        </div>
      </div>
      <div class="space-y-2">
        <FieldHint>{{ t('forms.restrictions.groups') }}</FieldHint>
        <div class="flex flex-wrap gap-2">
          <span class="px-2 py-1 text-xs rounded border border-primary bg-primary/15 text-primary">Anfänger</span>
          <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Fortgeschritten</span>
        </div>
      </div>
      <div class="space-y-2">
        <FieldHint>{{ t('forms.restrictions.tags') }}</FieldHint>
        <div class="flex flex-wrap gap-2">
          <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Wettkampfgruppe</span>
        </div>
      </div>
      <p class="text-xs text-(--text-muted) italic">{{ t('forms.restrictions.noRestrictions') }}</p>
    </div>
  </NeutralContainer>
</template>
