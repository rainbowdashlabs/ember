/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ProblemsToolbar from './ProblemsToolbar.vue'
import ProblemEntry from './ProblemEntry.vue'
import ProblemStacktrace from './ProblemStacktrace.vue'

const stacktrace = `MailService.send(MailService.java:42)
SmtpTransport.connect(SmtpTransport.java:118)
java.net.ConnectException: Connection refused`
</script>

<template>
  <ProblemsToolbar/>
  <div class="space-y-2">
    <ErrorContainer>
      <ProblemEntry level="ERROR" source="MailService"
                    message="MailSendException: Connection refused to smtp.example.com:587"
                    timestamp="12.05.2026, 14:32 — 12.05.2026, 16:10"
                    :count="3" expanded/>
      <ProblemStacktrace :trace="stacktrace"/>
    </ErrorContainer>
    <InfoContainer>
      <ProblemEntry level="WARN" source="KbRepository"
                    message="Slow query: KbRepository.search took 2340ms (threshold: 500ms)"
                    timestamp="12.05.2026, 11:15"/>
    </InfoContainer>
    <ErrorContainer>
      <ProblemEntry level="ERROR" source="TypstCompiler"
                    message="ProcessException: Process exited with code 1"
                    timestamp="11.05.2026, 09:04"/>
    </ErrorContainer>
  </div>
</template>
