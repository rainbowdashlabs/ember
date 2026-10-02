/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

/**
 * The way in for a new member where no setup mail can go out: a one-time password, shown once the
 * member is made, for the station's administration to hand over in person.
 */
const {t} = useI18n()

const issue = defineModel<boolean>({required: true})

const labelId = useId()
</script>

<template>
  <div class="flex items-center justify-between gap-4" data-testid="one-time-password-choice">
    <div>
      <span :id="labelId" class="text-sm font-medium">{{ t('setupMailChoice.oneTimePassword') }}</span>
      <p class="text-xs text-(--text-muted)">{{ t('setupMailChoice.oneTimePasswordHint') }}</p>
    </div>
    <ToggleInput v-model="issue" :aria-labelledby="labelId"/>
  </div>
</template>
