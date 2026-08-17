/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MailProviderCredentialFields from '@/components/mail/MailProviderCredentialFields.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()

const vendorKey = computed(() => String(route.params.vendor ?? '').toUpperCase())
const vendorName = computed(() => RELAY_PROVIDER_NAMES[vendorKey.value] ?? '')

watch(vendorName, name => {
  if (!name) router.replace({name: 'help-station-mailing'})
}, {immediate: true})

const steps = computed(() => {
  const out: string[] = []
  for (let i = 1; i <= 8; i++) {
    const key = `helpCenter.mailVendor.${vendorKey.value}.step${i}`
    if (!te(key)) break
    out.push(t(key))
  }
  return out
})

/**
 * How this provider is told to report back. A provider Ember cannot receive reports from has no
 * steps, and the section says so rather than staying silent about it.
 */
const webhookSteps = computed(() => {
  const out: string[] = []
  for (let i = 1; i <= 5; i++) {
    const key = `helpCenter.mailVendor.${vendorKey.value}.webhookStep${i}`
    if (!te(key)) break
    out.push(t(key))
  }
  return out
})

const webhookLink = computed(() => {
  const key = `helpCenter.mailVendor.${vendorKey.value}.webhookLink`
  return te(key) ? t(key) : ''
})

const demoUser = ref('user@example.com')
const demoSecret = ref('')
</script>

<template>
  <HelpArticle
      v-if="vendorName"
      :title="t('helpCenter.mailVendor.title', {vendor: vendorName})"
      :subtitle="t('helpCenter.mailVendor.subtitle', {vendor: vendorName})"
  >
    <HelpSection :title="t('helpCenter.mailVendor.stepsTitle')">
      <ol class="list-decimal ml-5 space-y-1">
        <li v-for="(step, i) in steps" :key="i">{{ step }}</li>
      </ol>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailVendor.formTitle')">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ vendorName }}</SectionHeader>
        <MailProviderCredentialFields
            v-model:user="demoUser"
            v-model:secret="demoSecret"
            :provider="vendorKey"
        />
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailVendor.webhookTitle')">
      <p>{{ t('helpCenter.mailVendor.webhookIntro') }}</p>
      <ol v-if="webhookSteps.length" class="list-decimal ml-5 space-y-1">
        <li v-for="(step, i) in webhookSteps" :key="i">{{ step }}</li>
      </ol>
      <p v-else>{{ t('helpCenter.mailVendor.webhookUnsupported') }}</p>
      <p v-if="webhookLink">
        <a :href="webhookLink" target="_blank" rel="noopener"
           class="text-(--primary) underline">{{ webhookLink }}</a>
      </p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.mailVendor.senderTip') }}</HelpTip>
  </HelpArticle>
</template>
