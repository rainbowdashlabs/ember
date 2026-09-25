/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ShareLinkPanel from './ShareLinkPanel.vue'
import {forms, stationManage} from '@/api'
import {getItem} from '@/api/storage'
import {FormVisibility, type Form} from '@/api/forms'
import {describeFailure, FailureKind, type Failure} from '@/util/failure'

/**
 * Where to send somebody so they can answer this form.
 *
 * <p>Which address that is follows from how far the form reaches. A publicly reachable form is
 * opened at its own address, and that is what is offered: it has a link as well, but handing out a
 * link for a form anybody can already reach would say the link protects something when it protects
 * nothing, and replacing it would withdraw nothing.
 *
 * <p>A form that answers at its link alone is offered that link, with a way to replace it. Asking
 * does not make one, so a form nobody means to send does not acquire a link because somebody opened
 * its editor.
 */
const props = defineProps<{
  form: Form
  /**
   * Whether the form around this is still being written. The link is real from the moment it is
   * made, but what it opens is the form as it was last saved, which is worth saying where somebody
   * is in the middle of changing it.
   */
  unsaved?: boolean
}>()

const {t} = useI18n()

const openlyAddressed = computed(() => props.form.visibility === FormVisibility.PUBLIC)

/**
 * The station's readable name where it has one, and its identifier otherwise. The public address
 * takes either, and the readable one is what somebody pastes into a message without wincing.
 */
const stationName = ref<string | null>(null)

const publicPath = computed(
    () => `/public/station/${stationName.value ?? getItem('station_id') ?? ''}/forms/${props.form.publicUid}`)

const token = ref<string | null>(null)
const failure = ref<Failure | null>(null)
const busy = ref(false)
const loading = ref(true)

async function load() {
  if (openlyAddressed.value) {
    try {
      stationName.value = (await stationManage.getStationInfo()).publicSlug ?? null
    } catch {
      stationName.value = null
    }
    loading.value = false
    return
  }
  loading.value = true
  failure.value = null
  try {
    token.value = await forms.getFormShareLink(props.form.id)
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    loading.value = false
  }
}

/**
 * Gives the form a link, or puts a new one in place of the one it has.
 *
 * <p>A refused replacement used to be reported as somebody else having replaced it first, whatever
 * had actually happened. That is one of several: the right to do it can be missing, the connection
 * can drop, the server can fall over. Only the server saying the link had moved on meant what the
 * reader was told, and acting on the other three by reloading and trying again got them nowhere.
 */
async function make(current: string | null) {
  busy.value = true
  failure.value = null
  try {
    token.value = await forms.replaceFormShareLink(props.form.id, current)
  } catch (e) {
    const described = describeFailure(e, t)
    failure.value = replacementWording(described, current)
  } finally {
    busy.value = false
  }
}

function replacementWording(described: Failure, current: string | null): Failure {
  if (described.kind === FailureKind.CONFLICT) {
    return {...described, message: t('shareLink.replaceConflict')}
  }
  if (current === null && described.kind === FailureKind.REJECTED) {
    return {...described, message: t('shareLink.createFailed')}
  }
  return described
}

onMounted(load)
watch(() => [props.form.id, props.form.visibility], load)
</script>

<template>
    <NeutralContainer v-if="!loading">
        <div class="space-y-3">
            <SubHeader>{{ t('shareLink.formTitle') }}</SubHeader>

            <Alert v-if="unsaved && !openlyAddressed && token" variant="info">
                {{ t('shareLink.activeOnSave') }}
            </Alert>

            <ShareLinkPanel v-if="openlyAddressed" :path="publicPath" :hint="t('shareLink.formPublicHint')"/>

            <ShareLinkPanel
                v-else-if="token"
                :path="`/f/${token}`"
                :busy="busy"
                :failure="failure"
                replaceable
                @replace="make(token)"
            />

            <template v-else>
                <FailureAlert :failure="failure"/>
                <MutedText tag="p" size="sm">{{ t('shareLink.formNone') }}</MutedText>
                <ButtonRow align="end">
                    <PrimaryButton :disabled="busy" :icon="['fas', 'link']" @click="make(null)">
                        {{ t('shareLink.create') }}
                    </PrimaryButton>
                </ButtonRow>
            </template>
        </div>
    </NeutralContainer>
</template>
