/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/** Taking the station's appointments away as a document, which asks for its own span of dates. */
const emit = defineEmits<{
  open: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex items-center justify-between">
    <SectionHeader>{{ t('events.export') }}</SectionHeader>
    <PrimaryButton :icon="['fas', 'file-export']" @click="emit('open')">
      {{ t('events.exportPdf') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
