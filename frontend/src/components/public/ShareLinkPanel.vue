/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useFlashMessage} from '@/composables/useFlashMessage'
import type {Failure} from '@/util/failure'

/**
 * The link something is sent with, with a way to copy it and a way to end it.
 *
 * <p>Shared by a form and a page, which are sent the same way and withdrawn the same way. The
 * address is built from the one the reader is already looking at rather than from configuration:
 * this is a person copying a link out of their own browser, not a crawler being told where to go.
 */
const props = defineProps<{
  /** The path the link opens, without an origin: `/f/…` for a form, `/s/…` for a page. */
  path: string
  /**
   * Whether this address can be withdrawn. An address somebody can already reach without it cannot:
   * offering to replace one would promise something it does not do.
   */
  replaceable?: boolean
  /** What to say about who this address reaches, where the general line does not fit. */
  hint?: string
  busy?: boolean
  /** What went wrong with the link, described, so the panel can say what to do about it. */
  failure?: Failure | null
}>()

const emit = defineEmits<{
  (e: 'replace'): void
}>()

const {t} = useI18n()
const {message, flash} = useFlashMessage(2000)

const confirming = ref(false)

const url = computed(() => (typeof window === 'undefined' ? props.path : `${window.location.origin}${props.path}`))
const copied = computed(() => message.value !== '')

function copy() {
  void navigator.clipboard.writeText(url.value).then(() => flash(url.value))
}

function replace() {
  confirming.value = false
  emit('replace')
}
</script>

<template>
    <div class="space-y-3">
        <FailureAlert :failure="failure"/>

        <div class="rounded-theme border border-(--border) px-3 py-2 font-mono text-sm break-all">{{ url }}</div>

        <MutedText tag="p" size="sm">{{ hint ?? t('shareLink.hint') }}</MutedText>

        <Alert v-if="confirming" variant="info">{{ t('shareLink.replaceWarning') }}</Alert>

        <ButtonRow align="end">
            <SecondaryButton :icon="['fas', 'copy']" @click="copy">
                {{ copied ? t('shareLink.copied') : t('shareLink.copy') }}
            </SecondaryButton>
            <template v-if="replaceable">
                <SecondaryButton v-if="!confirming" :icon="['fas', 'rotate']" @click="confirming = true">
                    {{ t('shareLink.replace') }}
                </SecondaryButton>
                <template v-else>
                    <SecondaryButton @click="confirming = false">{{ t('common.cancel') }}</SecondaryButton>
                    <PrimaryButton :disabled="busy" @click="replace">{{ t('shareLink.replaceConfirm') }}</PrimaryButton>
                </template>
            </template>
        </ButtonRow>
    </div>
</template>
