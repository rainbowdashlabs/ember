/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
const model = defineModel<boolean>({default: false})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
          v-if="model"
          class="fixed inset-0 z-50 flex items-center justify-center"
      >
        <!-- Backdrop -->
        <div
            class="absolute inset-0 bg-black/50"
            @click="model = false"
        />
        <!-- Content -->
        <div
            class="relative z-10 w-full max-w-lg mx-4 rounded-lg border border-bg-light-accent bg-bg-light p-6 shadow-xl dark:border-bg-dark-accent dark:bg-bg-dark">
          <button
              class="absolute top-3 right-3 p-1 text-[var(--text-muted)] hover:text-[var(--text)] transition-colors"
              @click="model = false"
          >
            <font-awesome-icon :icon="['fas', 'xmark']" class="h-5 w-5"/>
          </button>
          <slot/>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
