/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SecondaryContainer from '@/components/container/SecondaryContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Containers</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <PrimaryContainer>Primary container</PrimaryContainer>
      <SecondaryContainer>Secondary container</SecondaryContainer>
      <SuccessContainer>Success container</SuccessContainer>
      <ErrorContainer>Error container</ErrorContainer>
      <InfoContainer>Info container</InfoContainer>
      <NeutralContainer>Neutral container</NeutralContainer>
    </div>
  </section>
</template>
