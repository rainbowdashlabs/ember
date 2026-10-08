/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import SignaturePad from '@/components/input/SignaturePad.vue'
import {SignerCapacity} from '@/api/generated/schema'
import type {SigningMarkChoice} from './useSigningAct'

/**
 * The signature picture the act leaves in its field: the one the signer saved, or one drawn or typed here.
 *
 * <p>A signer with a saved picture signs with it unless they choose to draw a new one, and may keep a new
 * one for next time. A member signing through someone else's account always draws their own here, since
 * the account's picture is not theirs, and it is not kept for the account.
 */
const props = defineProps<{
  capacity: SignerCapacity
  memberName: string | null
  savedUrl: string | null
}>()

const choice = defineModel<SigningMarkChoice>({required: true})

const {t} = useI18n()
const keepId = useId()

const throughAccount = computed(() => props.capacity === SignerCapacity.MEMBER_THROUGH_ACCOUNT)
const offersSaved = computed(() => !throughAccount.value && props.savedUrl !== null)
const showsSaved = computed(() => offersSaved.value && choice.value.useSaved)

const draft = computed({
  get: () => choice.value.draft,
  set: (value) => { choice.value = {...choice.value, draft: value} },
})

const keep = computed({
  get: () => choice.value.keep,
  set: (value: boolean) => { choice.value = {...choice.value, keep: value} },
})

function useSaved(saved: boolean) {
  choice.value = {draft: null, useSaved: saved, keep: false}
}
</script>

<template>
  <section class="space-y-3" data-testid="signing-mark">
    <SubHeader>{{ t('signing.mark.heading') }}</SubHeader>
    <template v-if="showsSaved && savedUrl">
      <img
          :src="savedUrl"
          :alt="t('signing.mark.savedAlt')"
          class="block max-w-sm max-h-28 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-white p-2"
      />
      <MutedText tag="p" size="sm">{{ t('signing.mark.savedHint') }}</MutedText>
      <SecondaryButton type="button" :icon="['fas', 'pen']" data-testid="signing-mark-new" @click="useSaved(false)">
        {{ t('signing.mark.drawNew') }}
      </SecondaryButton>
    </template>
    <template v-else>
      <MutedText tag="p" size="sm">
        {{ throughAccount ? t('signing.mark.throughAccount', {name: memberName ?? ''}) : t('signing.mark.drawHint') }}
      </MutedText>
      <SignaturePad v-model="draft"/>
      <div v-if="!throughAccount" class="flex items-center gap-2">
        <CheckboxInput :id="keepId" v-model="keep"/>
        <FormLabel :for="keepId" class="mb-0">{{ t('signing.mark.keep') }}</FormLabel>
      </div>
      <SecondaryButton v-if="offersSaved" type="button" :icon="['fas', 'rotate-left']" @click="useSaved(true)">
        {{ t('signing.mark.useSaved') }}
      </SecondaryButton>
    </template>
  </section>
</template>
