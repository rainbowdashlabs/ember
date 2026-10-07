/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import NameRequestRow from './NameRequestRow.vue'
import DenyNameChangeModal from './DenyNameChangeModal.vue'
import {members} from '@/api'
import type {NameChangeView} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The new names members asked for, waiting above the other open changes until somebody approves
 * or denies them. Shows nothing while nobody waits.
 *
 * <p>A request decided meanwhile at another station of the same member is gone from the list after
 * the refusal that tells so, since the list is read again then.
 */
const {t} = useI18n()

const requests = ref<NameChangeView[]>([])
const failure = ref<Failure | null>(null)
const processing = ref(false)
const denying = ref<NameChangeView | null>(null)
const denyOpen = ref(false)
const denyReason = ref('')

async function load() {
  try {
    requests.value = await members.listNameChangeRequests()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function decide(decision: () => Promise<void>) {
  processing.value = true
  failure.value = null
  try {
    await decision()
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    processing.value = false
    await load()
  }
}

function approve(request: NameChangeView) {
  return decide(() => members.approveNameChange(request.id))
}

function startDeny(request: NameChangeView) {
  denying.value = request
  denyReason.value = ''
  denyOpen.value = true
}

async function submitDeny() {
  const request = denying.value
  if (!request) return
  await decide(() => members.denyNameChange(request.id, denyReason.value.trim() || null))
  denyOpen.value = false
}

onMounted(load)
</script>

<template>
  <section v-if="requests.length > 0 || failure" class="space-y-3" data-testid="name-requests">
    <SubHeader>{{ t('memberChanges.nameRequestsTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('memberChanges.nameRequestsHint') }}</MutedText>
    <FailureAlert :failure="failure"/>
    <NameRequestRow
        v-for="request in requests"
        :key="request.id"
        :request="request"
        :processing="processing"
        @approve="approve(request)"
        @deny="startDeny(request)"
    />
    <DenyNameChangeModal
        v-model="denyOpen"
        v-model:reason="denyReason"
        :request="denying"
        :processing="processing"
        @submit="submitDeny"
    />
  </section>
</template>
