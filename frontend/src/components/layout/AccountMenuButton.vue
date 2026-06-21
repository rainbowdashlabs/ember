/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import AccountMenu from '@/components/layout/AccountMenu.vue'
import IdentityButton from '@/components/button/IdentityButton.vue'
import {useSession} from '@/composables/useSession'
import {useLogout} from '@/composables/useLogout'

const router = useRouter()
const {sessionInfo, fullName} = useSession()
const {logout} = useLogout()

const open = ref(false)
const isDesktop = ref(false)
const rootEl = ref<HTMLElement | null>(null)

function updateBreakpoint() {
  isDesktop.value = window.matchMedia('(min-width: 1024px)').matches
}

function onDocumentClick(e: MouseEvent) {
  if (!open.value) return
  if (rootEl.value && !rootEl.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => {
  updateBreakpoint()
  window.addEventListener('resize', updateBreakpoint)
  window.addEventListener('mousedown', onDocumentClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateBreakpoint)
  window.removeEventListener('mousedown', onDocumentClick)
})

const accountUid = computed(() => sessionInfo.value?.account?.uid)
const displayName = computed(() => fullName())

function toggle() {
  open.value = !open.value
}

function goToSettings() {
  router.push({name: 'account'})
}
</script>

<template>
  <div ref="rootEl" class="relative">
    <IdentityButton @click="toggle">
      <UserAvatar
          :identity="accountUid ? { accountUid } : undefined"
          :name="displayName"
          size="sm"
      />
      <span class="hidden sm:inline">{{ displayName }}</span>
    </IdentityButton>

    <AccountMenu :mode="isDesktop ? 'dropdown' : 'drawer'" :open="open"
                 @close="open = false"
                 @settings="goToSettings"
                 @logout="logout"/>
  </div>
</template>
