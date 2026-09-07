<template>
  <div class="real-map-wrap">
    <div ref="mapEl" class="leaflet-map" aria-label="道路与小区真实地图" />
    <div class="map-status">
      <span class="live-dot" />道路地图
      <b>{{ communities.length }}</b> 个小区
    </div>
    <div class="map-help">拖动地图查看道路 · 滚轮缩放 · 点击小区查看住户</div>
    <div v-if="tileError" class="map-error">道路底图加载失败，请检查网络后重试</div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'

const props = defineProps({
  communities: { type: Array, default: () => [] },
  selectedCommunity: { type: Object, default: null }
})
const emit = defineEmits(['select-community'])

const mapEl = ref()
const tileError = ref(false)
let map
let layer

const tone = (community) => {
  const rate = Number(community.alarmRate || 0)
  if (rate >= 6) return 'danger'
  if (rate >= 3) return 'warn'
  return 'ok'
}

const markerIcon = (community) => L.divIcon({
  className: 'community-marker-host',
  html: `<button class="community-marker ${tone(community)}" type="button"><i></i><span>${community.name}</span><small>${community.meters}户</small></button>`,
  iconSize: [168, 48],
  iconAnchor: [18, 42]
})

const renderMarkers = () => {
  if (!map) return
  layer?.remove()
  layer = L.featureGroup().addTo(map)
  const valid = props.communities.filter((community) => Number.isFinite(Number(community.location?.lat)) && Number.isFinite(Number(community.location?.lng)))
  valid.forEach((community) => {
    const marker = L.marker([community.location.lat, community.location.lng], {
      icon: markerIcon(community),
      title: community.name,
      keyboard: true,
      alt: `${community.name}，${community.meters}户`
    })
    marker.on('click', () => emit('select-community', community))
    marker.addTo(layer)
  })

  const selected = props.selectedCommunity?.location
  if (selected?.lat && selected?.lng) map.flyTo([selected.lat, selected.lng], 16, { duration: .65 })
  else if (valid.length === 1) map.setView([valid[0].location.lat, valid[0].location.lng], 15)
  else if (valid.length) map.fitBounds(layer.getBounds().pad(.22), { maxZoom: 15 })
}

onMounted(async () => {
  await nextTick()
  map = L.map(mapEl.value, { zoomControl: false, minZoom: 9, maxZoom: 19 })
  L.control.zoom({ position: 'bottomright' }).addTo(map)
  const tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  })
  tiles.on('tileerror', () => { tileError.value = true })
  tiles.on('load', () => { tileError.value = false })
  tiles.addTo(map)
  renderMarkers()
  setTimeout(() => map?.invalidateSize(), 0)
})

watch(() => [props.communities, props.selectedCommunity], renderMarkers, { deep: true })
onBeforeUnmount(() => { map?.remove(); map = null })
</script>

<style>
@import 'leaflet/dist/leaflet.css';

.community-marker-host { overflow: visible !important; background: transparent; border: 0; }
.community-marker {
  display: grid; grid-template-columns: 22px minmax(0, auto); grid-template-rows: auto auto; align-items: center;
  width: max-content; min-width: 116px; max-width: 164px; min-height: 44px; padding: 5px 10px 5px 6px; border: 1px solid rgba(255,255,255,.34); border-radius: 13px;
  color: #fff; box-shadow: 0 5px 16px rgba(15,23,42,.3); cursor: pointer; text-align: left;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.community-marker i { grid-row: 1 / 3; width: 16px; height: 16px; border: 4px solid #fff; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); }
.community-marker span { min-width: 0; font-size: 12px; font-weight: 700; line-height: 1.3; white-space: normal; overflow-wrap: anywhere; }
.community-marker small { font-size: 10px; line-height: 1.25; opacity: .88; }
.community-marker:focus-visible { outline: 3px solid rgba(5,191,219,.38); outline-offset: 3px; }
.community-marker.ok { background: #087f8c; }
.community-marker.warn { background: #a77a18; }
.community-marker.danger { background: #c4573f; }
</style>

<style scoped>
.real-map-wrap { position: relative; height: 100%; min-height: 590px; overflow: hidden; border-radius: 14px; background: #dce8ed; }
.leaflet-map { position: absolute; inset: 0; }
.map-status, .map-help {
  position: absolute; z-index: 500; left: 14px; display: flex; align-items: center; gap: 7px;
  padding: 8px 11px; border-radius: 10px; background: rgba(255,255,255,.94); color: #334155;
  max-width: calc(100% - 28px); box-shadow: 0 5px 18px rgba(15,23,42,.13); font-size: 12px; line-height: 1.45;
  overflow-wrap: anywhere; backdrop-filter: blur(10px);
}
.map-error { position: absolute; z-index: 550; inset: auto 14px 14px auto; padding: 9px 12px; border-radius: 10px; background: rgba(190,65,45,.92); color: #fff; font-size: 12px; box-shadow: 0 5px 18px rgba(15,23,42,.2); }
.map-status { top: 14px; }
.map-help { bottom: 14px; color: #64748b; }
.live-dot { width: 8px; height: 8px; border-radius: 50%; background: #17a673; box-shadow: 0 0 0 4px rgba(23,166,115,.13); }
@media (max-width: 760px) { .real-map-wrap { min-height: 460px; } .map-help { display: none; } }
</style>
