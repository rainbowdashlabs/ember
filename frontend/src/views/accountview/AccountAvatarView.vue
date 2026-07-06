/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FileUploadField from '@/components/input/FileUploadField.vue'
import { members, session as sessionApi } from '@/api'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const { sessionInfo, loaded, load } = useSession()

const AVATAR_MAX_SIZE = 2 * 1024 * 1024
const avatarKey = ref(0)
const uploadingAvatar = ref(false)
const avatarError = ref<string | null>(null)
const loading = ref(true)
const error = ref('')

const editFirstName = ref('')
const editLastName = ref('')
const editEmail = ref('')

async function handleAvatarUpload(file: File) {
  uploadingAvatar.value = true
  avatarError.value = null
  try {
    await sessionApi.uploadAvatar(file)
    avatarKey.value++
  } catch {
    avatarError.value = t('fileUpload.uploadFailed')
  } finally {
    uploadingAvatar.value = false
  }
}

async function removeAvatar() {
  error.value = ''
  try {
    await sessionApi.deleteAvatar()
    avatarKey.value++
  } catch {
    error.value = t('common.error')
  }
}

const emailChangePending = ref(false)

async function saveAccount() {
  error.value = ''
  const account = sessionInfo.value?.account
  if (!account) return
  const emailChanged = editEmail.value.trim().toLowerCase() !== (account.email ?? '').toLowerCase()
  try {
    await members.updateAccount(account.id, {
      email: editEmail.value,
      firstName: editFirstName.value,
      lastName: editLastName.value,
    })
    emailChangePending.value = emailChanged
    await load()
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

function applyAccount() {
  const account = sessionInfo.value?.account
  if (!account) return
  editFirstName.value = account.firstName ?? ''
  editLastName.value = account.lastName ?? ''
  editEmail.value = account.email ?? ''
  loading.value = false
}

watch(loaded, (ready) => { if (ready) applyAccount() }, { immediate: true })

onMounted(() => {
  if (!loaded.value) load()
  else applyAccount()
})
</script>

<template>
  <ViewContent :title="t('pages.account-avatar.title')" :subtitle="t('pages.account-avatar.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profile.avatar') }}</SectionHeader>
          <div class="flex items-center gap-4">
            <UserAvatar
                :key="avatarKey"
                :identity="sessionInfo?.account?.uid ? { accountUid: sessionInfo.account.uid } : undefined"
                :name="(editFirstName + ' ' + editLastName).trim()"
                size="lg"
            />
            <div class="flex items-start gap-2">
              <FileUploadField
                  accept="image/png,image/jpeg,image/webp"
                  :max-size="AVATAR_MAX_SIZE"
                  :disabled="uploadingAvatar"
                  :error="avatarError"
                  :hint="t('profile.avatarHint')"
                  :label="uploadingAvatar ? t('common.loading') : t('profile.uploadAvatar')"
                  @select="handleAvatarUpload"
              />
              <DeleteButton @click="removeAvatar"/>
            </div>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profile.accountTitle') }}</SectionHeader>
          <div class="grid gap-4 sm:grid-cols-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('profile.firstName') }}</FieldLabel>
              <TextInput v-model="editFirstName"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('profile.lastName') }}</FieldLabel>
              <TextInput v-model="editLastName"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('profile.email') }}</FieldLabel>
              <TextInput v-model="editEmail"/>
            </div>
          </div>
          <Alert v-if="emailChangePending" variant="info">{{ t('profile.emailChangePending') }}</Alert>
          <SaveButton :action="saveAccount">{{ t('profile.saveAccount') }}</SaveButton>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
