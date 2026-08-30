/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {PasswordLinkStatus} from '@/api/auth'

/**
 * What to say instead of a form nobody could submit.
 *
 * <p>An expired link is not a mistake the reader made, and telling them only that something is
 * invalid leaves them re-clicking the same mail. What happened, and who can put it right: for an
 * invitation that is whoever invited them, because only a station can send another; for a reset it
 * is the reader themselves, who can ask for a new one on the spot.
 */
defineProps<{
  status: PasswordLinkStatus
}>()

const {t} = useI18n()
</script>

<template>
  <div class="w-full max-w-sm space-y-6 text-center" data-testid="link-no-longer-good">
    <PageHeroIcon :icon="['fas', status.standing === 'EXPIRED' ? 'hourglass-half' : 'link-slash']"/>
    <PageHeader class="text-2xl font-bold">
      {{ status.standing === 'EXPIRED' ? t('setPassword.expiredTitle') : t('setPassword.unknownTitle') }}
    </PageHeader>

    <p class="text-sm text-(--text-muted)">
      {{ status.standing === 'EXPIRED' ? t('setPassword.expiredText') : t('setPassword.unknownText') }}
    </p>

    <p v-if="status.purpose === 'SETUP'" class="text-sm text-(--text-muted)">
      {{ t('setPassword.askYourStation') }}
    </p>
    <p v-else class="text-sm text-(--text-muted)">{{ t('setPassword.askAgainYourself') }}</p>

    <div class="flex flex-col gap-2">
      <PrimaryButton v-if="status.purpose !== 'SETUP'" @click="$router.push({name: 'forgot-password'})">
        {{ t('setPassword.requestAnother') }}
      </PrimaryButton>
      <SecondaryButton @click="$router.push({name: 'login'})">{{ t('setPassword.backToLogin') }}</SecondaryButton>
    </div>
  </div>
</template>
