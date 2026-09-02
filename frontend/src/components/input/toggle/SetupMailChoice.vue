/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from './ToggleInput.vue'
import {sessionInfo} from '@/util/sessionState'

const {t} = useI18n()

const sendNow = defineModel<boolean>({default: true})

const props = withDefaults(defineProps<{
  /**
   * Whether an address is being given at all. Somebody entered without one is never written to, so
   * there is no choice to offer and the question is left out rather than asked and ignored. Most
   * callers write an address by definition and leave this alone.
   */
  hasAddress?: boolean
}>(), {hasAddress: true})

/**
 * Whether asking the question means anything on this instance.
 *
 * <p>An instance with no mail provider sends nothing at all, so both answers come to the same thing
 * and offering the choice would only promise something that cannot happen. The setup link is then
 * handed over by whoever runs the station, the same way it already is there.
 */
const canSendMail = computed(() => sessionInfo.value?.canSendMail !== false)
const offered = computed(() => canSendMail.value && props.hasAddress)
</script>

<template>
  <div v-if="offered" class="flex items-center justify-between gap-4" data-testid="setup-mail-choice">
    <div>
      <label class="text-sm font-medium">{{ t('setupMailChoice.label') }}</label>
      <p class="text-xs text-(--text-muted)">
        {{ sendNow ? t('setupMailChoice.hintNow') : t('setupMailChoice.hintLater') }}
      </p>
    </div>
    <ToggleInput v-model="sendNow"/>
  </div>
  <p
      v-else-if="!canSendMail && hasAddress"
      class="text-xs text-(--text-muted)"
      data-testid="setup-mail-impossible"
  >
    {{ t('setupMailChoice.noMailServer') }}
  </p>
</template>
