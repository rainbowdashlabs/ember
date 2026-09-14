/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'

const {t} = useI18n()
</script>

<template>
  <ButtonRow pair align="end">
    <SecondaryButton disabled>{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton disabled>{{ t('common.save') }}</PrimaryButton>
  </ButtonRow>
</template>
