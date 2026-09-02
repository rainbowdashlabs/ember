/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import AvatarSection from './accountavatarview/AvatarSection.vue'
import AccountDetailsSection from './accountavatarview/AccountDetailsSection.vue'
import { members } from '@/api'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const { sessionInfo, loaded, load } = useSession()

const loading = ref(true)
const error = ref('')

const editFirstName = ref('')
const editLastName = ref('')
const editEmail = ref('')
const editUsername = ref('')

const displayName = computed(() => (editFirstName.value + ' ' + editLastName.value).trim())

/**
 * Whether the address typed here is not the account's yet, because a link in the reader's mail
 * still has to be clicked. Read from the answer rather than guessed from what was typed: an
 * instance that cannot send at all, and an address nobody could read, both write it straight away.
 */
const emailChangePending = ref(false)

async function saveAccount() {
  error.value = ''
  const account = sessionInfo.value?.account
  if (!account) return
  try {
    const result = await members.updateAccount(account.id, {
      email: editEmail.value,
      username: editUsername.value,
      firstName: editFirstName.value,
      lastName: editLastName.value,
    })
    emailChangePending.value = result.emailChange === 'WAITING'
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
  editUsername.value = account.username ?? ''
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
        <AvatarSection
            :account-uid="sessionInfo?.account?.uid"
            :name="displayName"
            @error="v => error = v"
        />

        <AccountDetailsSection
            data-onboarding="account.email"
            v-model:first-name="editFirstName"
            v-model:last-name="editLastName"
            v-model:email="editEmail"
            v-model:username="editUsername"
            :email-change-pending="emailChangePending"
            :action="saveAccount"
        />
      </template>
    </div>
  </ViewContent>
</template>
