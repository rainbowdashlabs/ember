/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import OneTimePasswordDialog from '@/components/onetimepassword/OneTimePasswordDialog.vue'
import type {IssuedOneTimePassword} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'

/**
 * The end of the wizard. Where the new member was given a one-time password it opens at once, and
 * stays at hand for as long as this step is on the screen; a refused one says why.
 */
const props = defineProps<{
  oneTimePassword: IssuedOneTimePassword | null
  oneTimePasswordFailure: Failure | null
}>()

const {t} = useI18n()

const emit = defineEmits<{
  createAnother: []
  toList: []
}>()

const showingPassword = ref(props.oneTimePassword !== null)
</script>

<template>
  <NeutralContainer class="space-y-4 text-center py-8">
    <font-awesome-icon :icon="['fas', 'circle-check']" class="text-4xl text-success"/>
    <SectionHeader>{{ t('membersCreate.done') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('membersCreate.doneHint') }}</p>
    <FailureAlert :failure="oneTimePasswordFailure"/>
    <ButtonRow align="center">
      <SecondaryButton
          v-if="oneTimePassword"
          :icon="['fas', 'key']"
          data-testid="one-time-password-show"
          @click="showingPassword = true"
      >
        {{ t('membersCreate.showOneTimePassword') }}
      </SecondaryButton>
      <SecondaryButton @click="emit('createAnother')">{{ t('membersCreate.createAnother') }}</SecondaryButton>
      <PrimaryButton @click="emit('toList')">{{ t('membersCreate.toList') }}</PrimaryButton>
    </ButtonRow>
    <OneTimePasswordDialog v-if="oneTimePassword" v-model="showingPassword" :issued="oneTimePassword"/>
  </NeutralContainer>
</template>
