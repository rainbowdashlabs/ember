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
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {useConfirmAction} from '@/composables/useConfirmAction'

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

/** Removing the logo asks first, unless shift is held; the page removing it hears of it only once agreed. */
const {show: confirmingRemove, request: requestRemove, confirm: confirmRemove} = useConfirmAction<true>({
  onConfirm: async () => {
    emit('remove')
  },
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.logoTitle') }}</SectionHeader>

    <div v-if="props.hasLogo" class="space-y-3">
      <img
          v-if="props.logoObjectUrl"
          :src="props.logoObjectUrl"
          :alt="t('stationManage.logoAlt')"
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
        <DeleteButton @click="requestRemove(true)"/>
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

    <ConfirmDeleteModal
        v-model="confirmingRemove"
        :message="t('stationManage.logoDeleteConfirm')"
        @confirm="confirmRemove"
    />
  </NeutralContainer>
</template>
