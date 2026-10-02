/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import OneTimePasswordDialog from '@/components/onetimepassword/OneTimePasswordDialog.vue'
import type {IssuedOneTimePassword} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * The button that replaces somebody's password with a one-time password, asks first, and shows the
 * password once it is made. Whoever asks for it decides which door it goes through: a station's
 * member page and the instance's account list issue it on different terms.
 */
const props = defineProps<{
  /** Whose password is replaced, for the question asked first. */
  name: string
  /** Asks the server for the password. */
  issue: () => Promise<IssuedOneTimePassword>
}>()

const {t} = useI18n()
const confirming = ref(false)
const issued = ref<IssuedOneTimePassword | null>(null)
const showing = ref(false)

const {running, failure, run: confirm} = useAsyncAction(async () => {
  issued.value = await props.issue()
  confirming.value = false
  showing.value = true
})
</script>

<template>
  <div class="space-y-2">
    <FailureAlert :failure="failure"/>
    <SecondaryButton
        type="button"
        :icon="['fas', 'key']"
        data-testid="one-time-password-action"
        @click="confirming = true"
    >
      {{ t('oneTimePassword.action') }}
    </SecondaryButton>
    <ConfirmDeleteModal
        v-model="confirming"
        :title="t('oneTimePassword.confirmTitle')"
        :message="t('oneTimePassword.confirmText', {name})"
        :confirm-label="t('oneTimePassword.action')"
        :confirm-icon="['fas', 'key']"
        :busy="running"
        confirm-test-id="one-time-password-confirm"
        @confirm="confirm"
    />
    <OneTimePasswordDialog v-if="issued" v-model="showing" :issued="issued"/>
  </div>
</template>
