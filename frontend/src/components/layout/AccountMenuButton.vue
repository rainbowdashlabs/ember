/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useEventListener} from '@vueuse/core'
import {computed, ref} from 'vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import AccountMenu from '@/components/layout/AccountMenu.vue'
import IdentityButton from '@/components/button/IdentityButton.vue'
import {useSession} from '@/composables/useSession'
import {useLogout} from '@/composables/useLogout'

const {sessionInfo, fullName} = useSession()
const {logout} = useLogout()

const open = ref(false)
const {isDesktop} = useBreakpoint()
const rootEl = ref<HTMLElement | null>(null)

function onDocumentClick(e: MouseEvent) {
  if (!open.value) return
  if (rootEl.value && !rootEl.value.contains(e.target as Node)) {
    open.value = false
  }
}

useEventListener('mousedown', onDocumentClick)

const accountUid = computed(() => sessionInfo.value?.account?.uid)
const displayName = computed(() => fullName())

function toggle() {
  open.value = !open.value
}
</script>

<template>
  <div ref="rootEl" class="relative">
    <IdentityButton data-testid="account-menu" @click="toggle">
      <UserAvatar
          :identity="accountUid ? { accountUid } : undefined"
          :name="displayName"
          size="sm"
      />
      <span class="hidden sm:inline">{{ displayName }}</span>
    </IdentityButton>

    <AccountMenu :mode="isDesktop ? 'dropdown' : 'drawer'" :open="open"
                 @close="open = false"
                 @logout="logout"/>
  </div>
</template>
