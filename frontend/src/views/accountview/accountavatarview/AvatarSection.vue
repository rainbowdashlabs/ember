/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FileUploadField from '@/components/input/FileUploadField.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {session as sessionApi} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'

const props = defineProps<{
  accountUid?: string | null
  name: string
}>()

const emit = defineEmits<{
  (e: 'error', failure: Failure | null): void
}>()

const AVATAR_MAX_SIZE = 2 * 1024 * 1024

const {t} = useI18n()
const avatarKey = ref(0)
const uploading = ref(false)
const uploadError = ref<string | null>(null)

async function upload(file: File) {
  uploading.value = true
  uploadError.value = null
  try {
    await sessionApi.uploadAvatar(file)
    avatarKey.value++
  } catch (e) {
    uploadError.value = describeFailure(e, t).message
  } finally {
    uploading.value = false
  }
}

/** Deletes the picture, after asking unless shift is held. */
const {show: confirmingRemove, requestDelete, confirm: remove, failure: removeFailure} = useConfirmDelete<true>({
  onDelete: async () => {
    emit('error', null)
    await sessionApi.deleteAvatar()
    avatarKey.value++
  },
})
watch(removeFailure, failure => { if (failure) emit('error', failure) })
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profile.avatar') }}</SectionHeader>
    <div class="flex items-center gap-4">
      <UserAvatar
          :key="avatarKey"
          :identity="props.accountUid ? { accountUid: props.accountUid } : undefined"
          :name="props.name"
          size="lg"
      />
      <div class="flex items-start gap-2">
        <FileUploadField
            accept="image/png,image/jpeg,image/webp"
            :max-size="AVATAR_MAX_SIZE"
            :disabled="uploading"
            :error="uploadError"
            :hint="t('profile.avatarHint')"
            :label="uploading ? t('common.loading') : t('profile.uploadAvatar')"
            @select="upload"
        />
        <DeleteButton @click="requestDelete(true)"/>
      </div>
    </div>
    <ConfirmDeleteModal
        v-model="confirmingRemove"
        :message="t('profile.avatarDeleteConfirm')"
        @confirm="remove"
    />
  </NeutralContainer>
</template>
