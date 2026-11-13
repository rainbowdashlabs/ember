/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {MessagePersonResponse} from '@/api/generated/schema'
import {openDirect} from '@/api/messages'
import {showToast} from '@/util/toast'
import {describeFailure, FailureKind} from '@/util/failure'

/**
 * The button on a member card that opens the private conversation with that person, or starts it.
 * The card shows it only where the reader may write to them: not on the reader's own card, not for
 * somebody without messages or who blocked the reader, and not for a member of another station.
 *
 * <p>A refusal the request client already reports, as it does with a denied request, is not told
 * a second time.
 */
const props = defineProps<{
  person: MessagePersonResponse
}>()

const {t} = useI18n()
const router = useRouter()
const opening = ref(false)

async function write() {
  opening.value = true
  try {
    const conversation = await openDirect(props.person.uid)
    await router.push(`/messages/${conversation.uid}`)
  } catch (e) {
    const failure = describeFailure(e, t)
    if (failure.kind !== FailureKind.DENIED && failure.kind !== FailureKind.SIGNED_OUT) showToast(failure.message, 'error')
  } finally {
    opening.value = false
  }
}
</script>

<template>
  <SecondaryButton compact :icon="['fas', 'paper-plane']" :disabled="opening" data-testid="member-card-message"
                   @click="write">
    {{ t('memberCard.message') }}
  </SecondaryButton>
</template>
