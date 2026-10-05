/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import Modal from '@/components/feedback/Modal.vue'
import ConsentStep from '@/components/consent/ConsentStep.vue'
import {useLoginConsent} from '@/composables/useLoginConsent'
import {hasSessionCookie} from '@/api/sessionCookie'
import {isPublicRoute} from '@/util/publicRoute'
import {auth} from '@/api'

/**
 * The consent for whoever is signed in but was never asked, as happened to members who set up their
 * account from a link before the setup pages asked it themselves. It is the same step the sign-in
 * form shows, laid over the page.
 *
 * <p>Closing it leaves the question open, so it comes back on the next page. Accepting records the
 * terms against the account as signing in would. Declining the terms ends the session, because
 * using the application is what the terms are about; the sign-in form then says why.
 */
const route = useRoute()
const legal = useLoginConsent()
const open = ref(false)

function decide() {
  open.value = legal.consent.value === null && hasSessionCookie() && !isPublicRoute(route.path, route.meta)
}

onMounted(() => {
  decide()
  if (open.value) void legal.loadConsentText()
})

watch(() => route.path, decide)

watch(legal.consent, async (answer) => {
  if (answer === null) return
  open.value = false
  if (answer === 'accepted') {
    await legal.recordAfterLogin()
    return
  }
  await auth.logout().catch(() => null)
  window.location.href = '/login'
})
</script>

<template>
  <Modal v-model="open" size="2xl">
    <ConsentStep :legal="legal"/>
  </Modal>
</template>
