<!-- 空心圆环百分比 -->
<template>
  <div class="donut" :style="{ width: size + 'px', height: size + 'px' }">
    <svg :viewBox="`0 0 ${size} ${size}`" class="donut-svg">
      <circle
        class="track"
        :cx="cx"
        :cy="cy"
        :r="r"
        fill="none"
        :stroke-width="stroke"
      />
      <circle
        class="value"
        :cx="cx"
        :cy="cy"
        :r="r"
        fill="none"
        :stroke="color"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="offset"
        :transform="`rotate(-90 ${cx} ${cy})`"
      />
    </svg>
    <div class="center">
      <strong :style="{ fontSize: Math.round(size * 0.26) + 'px' }">{{ display }}</strong>
      <small v-if="unit">{{ unit }}</small>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  percent: { type: Number, default: 0 },
  size: { type: Number, default: 72 },
  stroke: { type: Number, default: 7 },
  color: { type: String, default: '#088395' },
  unit: { type: String, default: '%' }
})

const cx = computed(() => props.size / 2)
const cy = computed(() => props.size / 2)
const r = computed(() => (props.size - props.stroke) / 2 - 1)
const circumference = computed(() => 2 * Math.PI * r.value)
const clamped = computed(() => Math.max(0, Math.min(100, Number(props.percent) || 0)))
const offset = computed(() => circumference.value * (1 - clamped.value / 100))
const display = computed(() => {
  const n = clamped.value
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
})
</script>

<style scoped lang="scss">
.donut {
  position: relative;
  flex-shrink: 0;
}
.donut-svg {
  width: 100%;
  height: 100%;
  display: block;
  filter: drop-shadow(0 2px 6px rgba(8, 80, 100, 0.12));
}
.track {
  stroke: rgba(8, 131, 149, 0.12);
}
.value {
  transition: stroke-dashoffset 0.6s ease;
}
.center {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  text-align: center;
  line-height: 1.05;
  strong {
    font-family: var(--font-display);
    color: #0a3d4d;
  }
  small {
    font-size: 10px;
    color: #5a7a86;
  }
}
</style>
