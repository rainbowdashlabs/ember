/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex justify-end gap-3">
    <SecondaryButton disabled>{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton disabled>{{ t('common.save') }}</PrimaryButton>
  </div>
</template>
