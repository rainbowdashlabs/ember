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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure, type Failure} from '@/util/failure'
import AvatarSection from './accountavatarview/AvatarSection.vue'
import AccountDetailsSection from './accountavatarview/AccountDetailsSection.vue'
import NicknameSection from '@/components/member/NicknameSection.vue'
import { members } from '@/api'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const { sessionInfo, loaded, load } = useSession()

const loading = ref(true)
const failure = ref<Failure | null>(null)

const editFirstName = ref('')
const editLastName = ref('')
const editEmail = ref('')
const editUsername = ref('')
const editNickname = ref('')

/** A nickname belongs to a membership, so there is nothing to set without one. */
const hasMembership = computed(() => sessionInfo.value?.member != null)

const displayName = computed(() => (editFirstName.value + ' ' + editLastName.value).trim())

/**
 * Whether the address typed here is not the account's yet, because a link in the reader's mail
 * still has to be clicked. Read from the answer rather than guessed from what was typed: an
 * instance that cannot send at all, and an address nobody could read, both write it straight away.
 */
const emailChangePending = ref(false)

/**
 * Stores the account details, then reads them back.
 *
 * <p>The read is answered for separately: details that were stored and a page that then failed to
 * refresh used to report a refused save, and a reader told that types the change in again.
 */
async function saveAccount() {
  failure.value = null
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
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }
  await reloadAfterSave()
}

async function saveNickname() {
  failure.value = null
  const member = sessionInfo.value?.member
  if (!member) return
  try {
    await members.setNickname(member.id, editNickname.value.trim() || null)
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }
  await reloadAfterSave()
}

/** Reads the page back after a save that already landed, and says so where it cannot. */
async function reloadAfterSave() {
  try {
    await load()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

function applyAccount() {
  const account = sessionInfo.value?.account
  if (!account) return
  editFirstName.value = account.firstName ?? ''
  editLastName.value = account.lastName ?? ''
  editEmail.value = account.email ?? ''
  editUsername.value = account.username ?? ''
  editNickname.value = sessionInfo.value?.member?.nickname ?? ''
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
      <FailureAlert :failure="failure"/>

      <template v-if="!loading">
        <AvatarSection
            :account-uid="sessionInfo?.account?.uid"
            :name="displayName"
            @error="v => failure = v"
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

        <NicknameSection
            v-if="hasMembership"
            v-model="editNickname"
            :first-name="editFirstName"
            :last-name="editLastName"
            :action="saveNickname"
        />
      </template>
    </div>
  </ViewContent>
</template>
