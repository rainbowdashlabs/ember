/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'

const {t} = useI18n()
</script>

<template>
  <div class="text-center">
    <router-link class="text-sm text-primary hover:underline" to="/login">{{ t('apply.backToLogin') }}</router-link>
  </div>
</template>
