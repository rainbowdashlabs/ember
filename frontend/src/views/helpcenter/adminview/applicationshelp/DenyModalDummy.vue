/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="mt-4 space-y-4">
    <SectionHeader>{{ t('adminApplications.denyTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">Lisa Beispiel - DLRG Neustadt</p>
    <div class="space-y-1">
      <FieldLabel>{{ t('adminApplications.denyReasonLabel') }}</FieldLabel>
      <TextInput :model-value="''" :placeholder="t('adminApplications.denyReasonPlaceholder')" />
    </div>
    <ButtonRow pair align="end">
      <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
      <ErrorButton>{{ t('adminApplications.deny') }}</ErrorButton>
    </ButtonRow>
  </NeutralContainer>
</template>
