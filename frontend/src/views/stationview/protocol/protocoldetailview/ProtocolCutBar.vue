/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {TestProtocolSection} from '@/api/generated/schema'

/**
 * The bar standing above the protocol while a section is cut: it names the section, says where it
 * can go, and offers the top level and giving up. Pasting into a section is offered on that section.
 */
defineProps<{
  section: TestProtocolSection
  canPasteAtTop: boolean
}>()

defineEmits<{
  pasteAtTop: []
  cancel: []
}>()

const {t} = useI18n()
</script>

<template>
  <InfoContainer class="sticky top-2 z-10 space-y-2">
    <p class="text-sm">{{ t('protocol.cutHint', {name: section.name}) }}</p>
    <ButtonRow pair>
      <PrimaryButton v-if="canPasteAtTop" :icon="['fas', 'paste']" @click="$emit('pasteAtTop')">
        {{ t('protocol.pasteAtTop') }}
      </PrimaryButton>
      <SecondaryButton @click="$emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    </ButtonRow>
  </InfoContainer>
</template>
