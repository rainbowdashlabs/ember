/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ShareLinkPanel from './ShareLinkPanel.vue'
import {forms, stationManage} from '@/api'
import {getItem} from '@/api/storage'
import {FormVisibility, type Form} from '@/api/forms'

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
const error = ref('')
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
  error.value = ''
  try {
    token.value = await forms.getFormShareLink(props.form.id)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function make(current: string | null) {
  busy.value = true
  error.value = ''
  try {
    token.value = await forms.replaceFormShareLink(props.form.id, current)
  } catch {
    error.value = current === null ? t('shareLink.createFailed') : t('shareLink.replaceConflict')
  } finally {
    busy.value = false
  }
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
                :error="error"
                replaceable
                @replace="make(token)"
            />

            <template v-else>
                <Alert v-if="error" variant="error">{{ error }}</Alert>
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
