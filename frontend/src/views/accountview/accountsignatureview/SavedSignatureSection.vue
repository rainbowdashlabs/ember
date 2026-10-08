/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {SignatureSettingsResponse} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * The signature picture the account keeps, as the server cleaned it, with when and how it was made and
 * the way to delete it.
 */
const props = defineProps<{
  settings: SignatureSettingsResponse
  imageUrl: string | null
  busy: boolean
}>()

defineEmits<{remove: []}>()

const {t} = useI18n()

const madeLine = computed(() => {
  const source = props.settings.imageSource
  if (!source) return ''
  return t(`accountSignature.saved.made.${source}`, {time: formatDateTime(props.settings.imageSavedAt)})
})
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="signature-saved">
    <SectionHeader>{{ t('accountSignature.saved.heading') }}</SectionHeader>
    <template v-if="settings.hasImage && imageUrl">
      <img
          :src="imageUrl"
          :alt="t('accountSignature.saved.alt')"
          class="block max-w-xl max-h-40 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-white p-2"
      />
      <div class="flex items-center gap-2">
        <MutedText tag="p" size="sm">{{ madeLine }}</MutedText>
        <DeleteButton :disabled="busy" data-testid="signature-delete" @click="$emit('remove')"/>
      </div>
    </template>
    <MutedText v-else tag="p" size="sm">{{ t('accountSignature.saved.none') }}</MutedText>
  </NeutralContainer>
</template>
