/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

/** The point dialog with the bonus box ticked, as a still picture of the dialog the editor opens. */
const { t } = useI18n()
</script>

<template>
  <HelpSection :title="t('helpCenter.protocolDetail.bonusTitle')">
    <p>{{ t('helpCenter.protocolDetail.bonusText') }}</p>
    <p>{{ t('helpCenter.protocolDetail.bonusCountText') }}</p>
    <NeutralContainer>
      <SubHeader class="mb-3">{{ t('protocol.editItem') }}</SubHeader>
      <div class="space-y-3">
        <TextInput :model-value="t('helpCenter.sample.protocol.bowline')" :placeholder="t('protocol.itemLabel')" disabled />
        <TextAreaInput :model-value="''" :placeholder="t('protocol.description')" disabled />
        <div>
          <FieldLabel class="mb-1">{{ t('protocol.points') }}</FieldLabel>
          <DecimalInput :model-value="1" step="0.5" disabled />
        </div>
        <div>
          <FieldLabel inline><CheckboxInput :model-value="true" disabled /> {{ t('protocol.bonus') }}</FieldLabel>
          <p class="text-xs text-[var(--text-muted)] mt-1">{{ t('protocol.bonusHint') }}</p>
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton disabled>{{ t('common.save') }}</PrimaryButton>
        </div>
      </div>
    </NeutralContainer>
  </HelpSection>
</template>
