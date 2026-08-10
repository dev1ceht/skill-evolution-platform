<script setup lang="ts">
import axios from 'axios';
import { SmartCanteenApi } from './api/smartCanteenApi';
import WorkflowDashboard from './components/WorkflowDashboard.vue';

const api = new SmartCanteenApi(axios.create({ timeout: 10_000 }));
</script>

<template>
  <WorkflowDashboard :api="api" />
</template>
