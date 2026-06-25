/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FileUploadField from '@/components/input/FileUploadField.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const props = defineProps<{
  hasLogo: boolean
  logoObjectUrl: string | null
  uploading: boolean
  logoError: string | null
  maxSize: number
}>()

const emit = defineEmits<{
  (e: 'upload', file: File): void
  (e: 'remove'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.logoTitle') }}</SectionHeader>

    <div v-if="props.hasLogo" class="space-y-3">
      <img
          v-if="props.logoObjectUrl"
          :src="props.logoObjectUrl"
          alt="Station Logo"
          class="max-h-32 max-w-64 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent object-contain"
      />
      <div class="flex items-start gap-2">
        <FileUploadField
            accept="image/png,image/jpeg,image/webp,image/svg+xml"
            :max-size="props.maxSize"
            :disabled="props.uploading"
            :error="props.logoError"
            :hint="t('stationManage.logoHint')"
            :label="props.uploading ? t('common.loading') : t('stationManage.changeLogo')"
            @select="(file: File) => emit('upload', file)"
        />
        <DeleteButton @click="emit('remove')"/>
      </div>
    </div>

    <div v-else class="space-y-3">
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.noLogo') }}</p>
      <FileUploadField
          accept="image/png,image/jpeg,image/webp,image/svg+xml"
          :max-size="props.maxSize"
          :disabled="props.uploading"
          :error="props.logoError"
          :hint="t('stationManage.logoHint')"
          :label="props.uploading ? t('common.loading') : t('stationManage.uploadLogo')"
          @select="(file: File) => emit('upload', file)"
      />
    </div>
  </NeutralContainer>
</template>
