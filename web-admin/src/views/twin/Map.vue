<template>
  <div class="hz">
    <header class="top">
      <div class="top-copy">
        <p class="eyebrow">Hangzhou Water Network</p>
        <h1>杭州供水管网分区地图</h1>
        <p class="desc">一级全市总览 · 二级区县展开地图 · 管网节点逐级下钻</p>
      </div>
      <div class="top-actions">
        <button v-if="path.length" class="ghost" @click="goUp">← 返回上一级</button>
        <button class="ghost" @click="reset">全市总览</button>
      </div>
    </header>

    <nav class="crumb">
      <span class="level-badge">{{ levelBadge }}</span>
      <button :class="{ on: !path.length }" @click="reset">杭州市</button>
      <template v-for="(p, i) in path" :key="i">
        <i>/</i>
        <button :class="{ on: i === path.length - 1 }" @click="jumpTo(i)">{{ p.name }}</button>
      </template>
      <span class="meta">覆盖 {{ data.dataCoverage }} 区县 · 样本 {{ data.totalMeters.toLocaleString() }} 表 · 异常 {{ data.totalAlarms }}</span>
    </nav>

    <div class="legend" aria-label="异常图例">
      <span class="leg"><i class="swatch ok" />无异常 · 蓝</span>
      <span class="leg"><i class="swatch warn" />需关注</span>
      <span class="leg"><i class="swatch danger" />异常偏高</span>
      <span class="leg"><i class="swatch muted" />无样本</span>
    </div>

    <div class="stage">
      <section v-if="level === 'city'" class="map-panel">
        <div class="desk">
          <div class="desk-shadow" aria-hidden="true" />
          <div class="map-canvas">
            <svg
              class="city-svg"
              :viewBox="`0 0 ${mapShapes.width} ${mapShapes.height}`"
              xmlns="http://www.w3.org/2000/svg"
            >
              <defs>
                <filter id="softLift" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="2.2" stdDeviation="2.4" flood-color="#0b2430" flood-opacity="0.18" />
                </filter>
                <linearGradient id="deskGlow" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stop-color="#e8f4fb" />
                  <stop offset="100%" stop-color="#f7fbfd" />
                </linearGradient>
              </defs>
              <rect width="100%" height="100%" fill="url(#deskGlow)" />
              <path
                v-if="mapShapes.frame?.d"
                class="city-frame"
                :d="mapShapes.frame.d"
                fill="none"
                stroke="#8aa3b3"
                stroke-width="1.2"
                stroke-linejoin="round"
                opacity="0.45"
              />
              <g
                v-for="shape in mapShapes.shapes"
                :key="shape.name"
                class="district-g"
                :class="{ active: hover === shape.name, muted: isMuted(shape) }"
                filter="url(#softLift)"
                @mouseenter="hover = shape.name"
                @mouseleave="hover = ''"
                @click="onShapeClick(shape)"
              >
                <g :transform="shapeTransform(shape)">
                  <path
                    :d="shape.d"
                    fill-rule="evenodd"
                    :fill="shapeFill(shape)"
                    :stroke="shapeStroke(shape)"
                    stroke-width="1.1"
                    stroke-linejoin="round"
                  />
                </g>
              </g>
              <text
                v-for="shape in mapShapes.shapes"
                :key="'lbl-' + shape.name"
                class="district-label"
                :x="shape.cx"
                :y="shape.cy"
                text-anchor="middle"
                dominant-baseline="middle"
                pointer-events="none"
              >{{ shape.name }}</text>
            </svg>
          </div>
        </div>
      </section>

      <section v-else-if="level === 'district' || level === 'zone' || level === 'community'" class="map-panel submap-panel">
        <div class="grid-head in-map">
          <div>
            <h2>{{ currentTitle }}</h2>
            <p>{{ currentHint }}</p>
          </div>
          <div class="map-tools" v-if="useRingMap">
            <span class="chip-note real">道路底图 · 街道与小区真实定位</span>
            <a v-if="selectedCommunity" class="nav-link" :href="navigationUrl(selectedCommunity)" target="_blank" rel="noopener">
              导航到 {{ selectedCommunity.name }} ↗
            </a>
          </div>
          <span v-else class="chip-note">SVG 矢量展开 · 点击片区继续下钻</span>
        </div>
        <div v-if="!currentTiles.length && level !== 'community'" class="empty-box">
          <div class="empty-chip muted">无样本</div>
          <h3>暂无样本数据</h3>
          <p>该区域可进入，但当前 CSV 未覆盖。可返回其他有数据区县继续下钻。</p>
        </div>
        <div v-else-if="!activeDistrictShape" class="empty-box">
          <div class="empty-chip muted">无轮廓</div>
          <h3>缺少区县矢量</h3>
          <p>当前区县没有可用轮廓，无法绘制二次划分地图。</p>
        </div>
        <div v-else-if="level === 'district'" class="desk">
          <div class="desk-shadow" aria-hidden="true" />
          <div class="map-canvas sub" @wheel.prevent="onMapWheel">
            <svg class="city-svg" :viewBox="subViewBox" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <clipPath :id="subClipId">
                  <path :d="activeDistrictShape.d" fill-rule="evenodd" />
                </clipPath>
                <filter id="softLiftSub" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="1.6" stdDeviation="1.8" flood-color="#0b2430" flood-opacity="0.16" />
                </filter>
                <linearGradient id="deskGlowSub" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stop-color="#e8f4fb" />
                  <stop offset="100%" stop-color="#f7fbfd" />
                </linearGradient>
              </defs>
              <rect :x="subPad.x" :y="subPad.y" :width="subPad.w" :height="subPad.h" fill="url(#deskGlowSub)" />

              <path
                :d="activeDistrictShape.d"
                fill-rule="evenodd"
                fill="rgba(186, 205, 218, 0.35)"
                stroke="rgba(15,23,42,0.4)"
                stroke-width="1.4"
              />

              <!-- 二级区县：完整轮廓内展开的可交互矢量分区 -->
              <g v-if="level === 'district'" :clip-path="`url(#${subClipId})`" filter="url(#softLiftSub)">
                <g
                  v-for="cell in subCells"
                  :key="cell.name"
                  class="district-g"
                  :class="{ active: hover === cell.name }"
                  @mouseenter="hover = cell.name"
                  @mouseleave="hover = ''"
                  @click="enterTile(cell.node)"
                >
                  <path
                    :d="cell.d"
                    :fill="tileFill(cell.node)"
                    stroke="rgba(255,255,255,0.35)"
                    stroke-width="0.6"
                    stroke-linejoin="round"
                  />
                </g>
              </g>

              <g v-if="level === 'district'" class="network-lines" :clip-path="`url(#${subClipId})`" pointer-events="none">
                <path
                  v-for="(line, index) in districtNetworkLines"
                  :key="'pipe-' + index"
                  :d="line.d"
                  :class="line.main ? 'network-main' : 'network-branch'"
                  fill="none"
                />
                <circle
                  v-for="(node, index) in districtNetworkNodes"
                  :key="'node-' + index"
                  :cx="node.x"
                  :cy="node.y"
                  :r="node.main ? 2.2 : 1.35"
                  :class="node.main ? 'network-node main' : 'network-node'"
                />
              </g>

              <!-- 片区/小区：空心圆环（可聚合） -->
              <g v-else :clip-path="`url(#${subClipId})`">
                <g
                  v-for="ring in displayRings"
                  :key="ring.id"
                  class="ring-g"
                  :class="{ active: hover === ring.id, cluster: ring.clustered }"
                  @mouseenter="hover = ring.id"
                  @mouseleave="hover = ''"
                  @click="onRingClick(ring)"
                >
                  <circle
                    :cx="ring.cx"
                    :cy="ring.cy"
                    :r="ringOuter(ring)"
                    fill="rgba(255,255,255,0.55)"
                    :stroke="ringStroke(ring)"
                    stroke-width="2.4"
                  />
                  <circle
                    :cx="ring.cx"
                    :cy="ring.cy"
                    :r="Math.max(2.2, ringOuter(ring) * 0.28)"
                    :fill="ringStroke(ring)"
                    opacity="0.9"
                  />
                  <text
                    class="ring-label"
                    :x="ring.cx"
                    :y="ring.cy + ringOuter(ring) + 7"
                    text-anchor="middle"
                  >{{ ringCaption(ring) }}</text>
                  <text
                    class="ring-pct"
                    :x="ring.cx"
                    :y="ring.cy + 1.2"
                    text-anchor="middle"
                    dominant-baseline="middle"
                  >{{ ring.alarmRate }}%</text>
                </g>
              </g>

              <path
                :d="activeDistrictShape.d"
                fill="none"
                fill-rule="evenodd"
                stroke="#0f172a"
                stroke-width="1.8"
                stroke-linejoin="round"
                opacity="0.5"
                pointer-events="none"
              />

              <g v-if="level === 'district'" :clip-path="`url(#${subClipId})`">
                <text
                  v-for="cell in subCells"
                  :key="'lbl-' + cell.name"
                  class="district-label sub-label"
                  :x="cell.cx"
                  :y="cell.cy"
                  :style="{ fontSize: `${subLabelSize}px` }"
                  text-anchor="middle"
                  dominant-baseline="middle"
                  pointer-events="none"
                >{{ cell.name }}</text>
                <text
                  v-for="cell in subCells"
                  :key="'stat-' + cell.name"
                  class="district-stat"
                  :x="cell.cx"
                  :y="cell.cy + subLabelSize * 1.25"
                  :style="{ fontSize: `${subStatSize}px` }"
                  text-anchor="middle"
                  dominant-baseline="middle"
                  pointer-events="none"
                >{{ cell.node.meters }} 表 · {{ cell.node.alarmRate }}%</text>
              </g>
            </svg>
          </div>
        </div>
        <div v-else class="real-map-shell">
          <CommunityRealMap
            :communities="mapCommunities"
            :selected-community="selectedCommunity"
            @select-community="selectCommunity"
          />
        </div>
      </section>

      <aside class="side">
        <div class="side-card">
          <div class="side-top">
            <div class="status-block" :class="sideTone">
              <strong>{{ sideStats.alarmRate }}%</strong>
              <small>异常占比</small>
            </div>
            <div>
              <h3>{{ sideTitle }}</h3>
              <p class="tag" :class="sideTone">{{ sideStatus }}</p>
            </div>
          </div>
          <div class="rate-track" aria-hidden="true">
            <i :class="sideTone" :style="{ width: Math.min(100, Number(sideStats.alarmRate) || 0) + '%' }" />
          </div>
          <dl class="kpis">
            <div><dt>水表</dt><dd>{{ sideStats.meters }}</dd></div>
            <div><dt>异常</dt><dd>{{ sideStats.alarmCount }}</dd></div>
            <div><dt>异常占比</dt><dd>{{ sideStats.alarmRate }}%</dd></div>
            <div><dt>用水量</dt><dd>{{ sideStats.usage }}</dd></div>
          </dl>
        </div>

        <div v-if="selectedMeter" class="side-card meter">
          <h4>{{ selectedMeter.meterId }}</h4>
          <p>{{ selectedMeter.householdName }}</p>
          <p class="meter-address">{{ selectedMeter.address }}</p>
          <ul>
            <li><span>状态</span><b :class="selectedMeter.alarm ? 'bad' : 'ok'">{{ selectedMeter.status }}</b></li>
            <li><span>用量</span><b>{{ selectedMeter.waterUsage }}</b></li>
            <li><span>流量</span><b>{{ selectedMeter.flowRate }}</b></li>
            <li><span>压力</span><b>{{ selectedMeter.pressure }}</b></li>
            <li><span>水温</span><b>{{ selectedMeter.temperature }}℃</b></li>
            <li><span>时间</span><b>{{ selectedMeter.timestamp }}</b></li>
          </ul>
          <a class="nav-link full" :href="navigationUrl(selectedMeter)" target="_blank" rel="noopener">导航到该住户所在小区 ↗</a>
        </div>

        <div v-if="selectedCommunity" class="side-card households">
          <div class="household-head">
            <div>
              <span class="side-eyebrow">住户清单</span>
              <h4>{{ selectedCommunity.name }}</h4>
            </div>
            <b>{{ selectedCommunity.metersDetail.length }} 户</b>
          </div>
          <label class="household-search">
            <span>搜索</span>
            <input v-model.trim="householdQuery" placeholder="户号、表号或楼栋房号" />
          </label>
          <div class="household-list">
            <button
              v-for="meter in filteredHouseholds"
              :key="meter.meterId"
              type="button"
              class="household-row"
              :class="{ active: selectedMeter?.meterId === meter.meterId }"
              @click="pickMeter(meter)"
            >
              <span class="household-state" :class="meter.alarm ? 'bad' : 'ok'" />
              <span class="household-main">
                <strong>{{ meter.householdName }}</strong>
                <small>{{ meter.address.replace(selectedCommunity.address, '') }} · {{ meter.meterId }}</small>
              </span>
              <span class="household-usage">{{ meter.waterUsage }}<small>m³</small></span>
            </button>
            <div v-if="!filteredHouseholds.length" class="household-empty">没有匹配的住户</div>
          </div>
        </div>

        <div v-else class="side-card tip">
          <em>提示</em>
          <p>{{ tipText }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import CommunityRealMap from '@/components/CommunityRealMap.vue'
import data from '@/data/hangzhouMapData.js'
import mapShapes from '@/data/hangzhouShapes.js'
import {
  clusterCellSize,
  clusterRings,
  fitViewBox,
  partitionExpandedMap,
  pathBBox,
  placeRings,
  ringRadius
} from '@/utils/subdivideMap'

const path = ref([])
const hover = ref('')
const selectedMeter = ref(null)
const mapZoom = ref(3)
const householdQuery = ref('')

const districtByName = computed(() => {
  const m = {}
  data.districts.forEach((d) => { m[d.name] = d })
  return m
})

const shapeByName = computed(() => {
  const m = {}
  mapShapes.shapes.forEach((s) => {
    m[s.name] = s
    ;(s.names || []).forEach((n) => { m[n] = s })
  })
  return m
})

const level = computed(() => (path.value.length ? path.value[path.value.length - 1].type : 'city'))
const currentDistrict = computed(() => path.value.find((p) => p.type === 'district')?.node || null)
const currentZone = computed(() => path.value.find((p) => p.type === 'zone')?.node || null)
const selectedCommunity = computed(() => path.value.find((p) => p.type === 'community')?.node || null)
const mapCommunities = computed(() => currentZone.value?.communities || [])
const filteredHouseholds = computed(() => {
  const list = selectedCommunity.value?.metersDetail || []
  const query = householdQuery.value.toLowerCase()
  if (!query) return list
  return list.filter((meter) => [meter.householdNo, meter.householdName, meter.meterId, meter.address]
    .some((value) => String(value || '').toLowerCase().includes(query)))
})

const useRingMap = computed(() => level.value === 'zone' || level.value === 'community')

const currentTiles = computed(() => {
  if (level.value === 'district') return currentDistrict.value?.zones || []
  if (level.value === 'zone') return currentZone.value?.communities || []
  return []
})

const activeDistrictShape = computed(() => {
  const name = currentDistrict.value?.name
  return name ? shapeByName.value[name] : null
})

const districtBBox = computed(() => {
  const shape = activeDistrictShape.value
  if (!shape?.d) return null
  return pathBBox(shape.d)
})

const zoneCells = computed(() => {
  if (!districtBBox.value || !currentDistrict.value?.zones?.length) return []
  return partitionExpandedMap(
    districtBBox.value,
    currentDistrict.value.zones.map((t) => ({ name: t.name, node: t }))
  )
})

const activeZoneCell = computed(() =>
  zoneCells.value.find((c) => c.name === currentZone.value?.name) || null
)

const subCells = computed(() => {
  if (level.value !== 'district' || !currentTiles.value.length || !districtBBox.value) return []
  return zoneCells.value
})

// SVG text scales with each district viewBox. Derive the label size from the
// shorter side of the active outline so names stay readable without colliding.
const subLabelSize = computed(() => {
  const box = districtBBox.value
  if (!box) return 3
  return Number(Math.max(1.8, Math.min(5, Math.min(box.w, box.h) / 14)).toFixed(2))
})
const subStatSize = computed(() => Number(Math.max(1.05, subLabelSize.value * .56).toFixed(2)))

const districtNetworkLines = computed(() => {
  const box = districtBBox.value
  if (!box) return []
  const { minX: x, minY: y, w, h } = box
  return [
    { main: true, d: `M ${x - w * .05} ${y + h * .58} C ${x + w * .2} ${y + h * .38}, ${x + w * .54} ${y + h * .72}, ${x + w * 1.05} ${y + h * .42}` },
    { main: true, d: `M ${x + w * .18} ${y - h * .05} C ${x + w * .32} ${y + h * .26}, ${x + w * .7} ${y + h * .48}, ${x + w * .76} ${y + h * 1.05}` },
    { d: `M ${x + w * .02} ${y + h * .24} C ${x + w * .28} ${y + h * .3}, ${x + w * .48} ${y + h * .12}, ${x + w * .98} ${y + h * .2}` },
    { d: `M ${x + w * .05} ${y + h * .82} C ${x + w * .36} ${y + h * .67}, ${x + w * .62} ${y + h * .92}, ${x + w * .95} ${y + h * .76}` },
    { d: `M ${x + w * .42} ${y - h * .04} C ${x + w * .48} ${y + h * .24}, ${x + w * .27} ${y + h * .56}, ${x + w * .32} ${y + h * 1.04}` },
    { d: `M ${x + w * .9} ${y - h * .02} C ${x + w * .72} ${y + h * .25}, ${x + w * .88} ${y + h * .63}, ${x + w * .58} ${y + h * 1.02}` }
  ]
})

const districtNetworkNodes = computed(() => {
  const box = districtBBox.value
  if (!box) return []
  const { minX: x, minY: y, w, h } = box
  return [
    { x: x + w * .2, y: y + h * .52 },
    { x: x + w * .37, y: y + h * .48, main: true },
    { x: x + w * .57, y: y + h * .62 },
    { x: x + w * .73, y: y + h * .5, main: true },
    { x: x + w * .49, y: y + h * .2 },
    { x: x + w * .34, y: y + h * .77 }
  ]
})

const ringFocusBox = computed(() => {
  if (level.value === 'community' && activeZoneCell.value?.bbox) return activeZoneCell.value.bbox
  if (level.value === 'zone' && activeZoneCell.value?.bbox) return activeZoneCell.value.bbox
  return districtBBox.value
})

const rawRings = computed(() => {
  const box = ringFocusBox.value
  if (!box) return []
  if (level.value === 'zone') {
    return placeRings(box, (currentZone.value?.communities || []).map((c) => ({
      name: c.name,
      node: c,
      meters: c.meters,
      alarmCount: c.alarmCount,
      alarmRate: c.alarmRate
    })))
  }
  if (level.value === 'community') {
    const meters = selectedCommunity.value?.metersDetail || []
    return placeRings(box, meters.map((m) => ({
      name: m.meterId,
      id: m.meterId,
      node: m,
      meters: 1,
      alarmCount: m.alarm ? 1 : 0,
      alarmRate: m.alarm ? 100 : 0
    })), 0.1)
  }
  return []
})

const displayRings = computed(() => {
  const box = ringFocusBox.value
  if (!rawRings.value.length || !box) return []
  const cell = clusterCellSize(box, mapZoom.value)
  return clusterRings(rawRings.value, cell)
})

const subViewBox = computed(() => {
  if (useRingMap.value && ringFocusBox.value) {
    // 放大时收窄视野（比例尺更大）
    const pad = 0.22 - (mapZoom.value - 1) * 0.03
    return fitViewBox(ringFocusBox.value, Math.max(0.06, pad))
  }
  return fitViewBox(districtBBox.value, 0.1)
})
const subPad = computed(() => {
  const parts = String(subViewBox.value).split(/\s+/).map(Number)
  return { x: parts[0] || 0, y: parts[1] || 0, w: parts[2] || 100, h: parts[3] || 100 }
})
const subClipId = computed(() => `clip-${(currentDistrict.value?.name || 'd').replace(/\W/g, '')}`)

const currentTitle = computed(() => {
  if (level.value === 'district') return `${currentDistrict.value?.name} · 二级展开地图`
  if (level.value === 'zone') return `${currentZone.value?.name} · 道路与小区地图`
  if (level.value === 'community') return `${selectedCommunity.value?.name} · 小区定位`
  return '杭州市'
})
const currentHint = computed(() => {
  if (level.value === 'district') return '区县真实轮廓已放大展开；颜色表示异常占比，点击片区继续下钻'
  if (level.value === 'zone') return '真实道路底图显示街道和小区位置；点击小区，在右侧展开全部住户'
  if (level.value === 'community') return '已定位到小区范围；右侧可搜索、点选每一户并打开导航'
  return ''
})

const levelBadge = computed(() => {
  if (level.value === 'city') return '一级区域'
  if (level.value === 'district') return '二级区域'
  if (level.value === 'zone') return '三级实景地图'
  return '小区住户'
})

watch(level, (lv) => {
  mapZoom.value = lv === 'community' ? 4 : 3
  hover.value = ''
})

const bumpZoom = (delta) => {
  mapZoom.value = Math.max(1, Math.min(5, mapZoom.value + delta))
}
const onMapWheel = (e) => {
  bumpZoom(e.deltaY > 0 ? -1 : 1)
}

const ringOuter = (ring) => ringRadius(ringFocusBox.value, ring.count || 1, !!ring.clustered)
const ringStroke = (ring) => {
  const rate = Number(ring.alarmRate || 0)
  const hasData = (ring.meters || 0) > 0 || ring.count > 0
  if (!hasData) return '#94adbe'
  if (rate >= 6) return '#e07a5f'
  if (rate >= 3) return '#c4a35a'
  return '#0284c7'
}
const ringCaption = (ring) => {
  if (ring.clustered) return `${ring.count}处 · ${ring.meters}表`
  return ring.name
}

const onRingClick = (ring) => {
  if (ring.clustered) {
    // 点聚合 → 放大拆开
    bumpZoom(1)
    return
  }
  if (level.value === 'zone' && ring.node) {
    enterTile(ring.node)
    return
  }
  if (level.value === 'community' && ring.node) {
    pickMeter(ring.node)
  }
}

const shapeStats = (shape) => {
  const names = shape.names || [shape.name]
  const list = names.map((n) => districtByName.value[n]).filter(Boolean)
  const meters = list.reduce((s, d) => s + (d.meters || 0), 0)
  const alarms = list.reduce((s, d) => s + (d.alarmCount || 0), 0)
  const hasData = list.some((d) => d.hasData)
  const alarmRate = meters ? Number(((alarms / meters) * 100).toFixed(1)) : 0
  return { meters, alarms, hasData, alarmRate, list }
}

const isMuted = (shape) => !shapeStats(shape).hasData

const toneClass = (rate, hasData) => {
  if (!hasData) return 'muted'
  if (rate >= 6) return 'danger'
  if (rate >= 3) return 'warn'
  return 'ok'
}

const statusText = (rate, hasData) => {
  if (!hasData) return '无样本'
  if (rate >= 6) return '异常偏高'
  if (rate >= 3) return '需关注'
  return '无异常'
}

/** 无异常=蓝；关注=金；偏高=珊瑚；无样本=灰蓝 */
const shapeFill = (shape) => {
  const { alarmRate, hasData } = shapeStats(shape)
  if (!hasData) return 'rgba(148, 173, 190, 0.55)'
  if (alarmRate >= 6) return hover.value === shape.name ? 'rgba(224, 122, 95, 0.95)' : 'rgba(224, 122, 95, 0.86)'
  if (alarmRate >= 3) return hover.value === shape.name ? 'rgba(196, 163, 90, 0.92)' : 'rgba(196, 163, 90, 0.82)'
  return hover.value === shape.name ? 'rgba(56, 152, 216, 0.98)' : 'rgba(2, 132, 199, 0.88)'
}
const shapeStroke = (shape) => {
  const { alarmRate, hasData } = shapeStats(shape)
  if (!hasData) return 'rgba(255,255,255,0.7)'
  if (alarmRate >= 6) return 'rgba(255, 236, 230, 0.95)'
  if (alarmRate >= 3) return 'rgba(255, 248, 230, 0.95)'
  return 'rgba(232, 244, 252, 0.95)'
}

const shapeTransform = (shape) => {
  if (shape.absolute) return ''
  const rot = shape.rotate || 0
  const x = shape.x || 0
  const y = shape.y || 0
  const sx = (shape.w && shape.nativeW) ? shape.w / shape.nativeW : (shape.scale || 1)
  const sy = (shape.h && shape.nativeH) ? shape.h / shape.nativeH : (shape.scale || 1)
  return `translate(${x} ${y}) rotate(${rot} ${(shape.nativeW || 0) / 2} ${(shape.nativeH || 0) / 2}) scale(${sx} ${sy})`
}

const sideTitle = computed(() => {
  if (selectedMeter.value) return '水表详情'
  return selectedCommunity.value?.name || currentZone.value?.name || currentDistrict.value?.name || '杭州市总览'
})

const sideStats = computed(() => {
  const node = selectedCommunity.value || currentZone.value || currentDistrict.value
  if (node) {
    return {
      meters: node.meters,
      alarmRate: node.alarmRate,
      alarmCount: node.alarmCount,
      usage: node.usage
    }
  }
  return {
    meters: data.totalMeters,
    alarmRate: Number(((data.totalAlarms / data.totalMeters) * 100).toFixed(1)),
    alarmCount: data.totalAlarms,
    usage: data.districts.reduce((s, d) => s + d.usage, 0).toFixed(3)
  }
})

const sideStatus = computed(() => {
  if (!path.value.length) return '全市总览'
  return statusText(Number(sideStats.value.alarmRate || 0), sideStats.value.meters > 0)
})
const sideTone = computed(() => {
  if (sideStats.value.meters === 0 && path.value.length) return 'muted'
  return toneClass(Number(sideStats.value.alarmRate || 0), true)
})

const tileFill = (node) => {
  const rate = Number(node?.alarmRate || 0)
  const hasData = (node?.meters || 0) > 0
  const name = node?.name
  if (!hasData) return 'rgba(148, 173, 190, 0.72)'
  if (rate >= 6) return hover.value === name ? 'rgba(224, 122, 95, 0.96)' : 'rgba(224, 122, 95, 0.9)'
  if (rate >= 3) return hover.value === name ? 'rgba(196, 163, 90, 0.94)' : 'rgba(196, 163, 90, 0.88)'
  return hover.value === name ? 'rgba(56, 152, 216, 0.98)' : 'rgba(2, 132, 199, 0.9)'
}

const tipText = computed(() => {
  if (level.value === 'city') return '当前为一级区域。点击任一区县，进入放大的二级矢量地图。'
  if (level.value === 'district') return '当前为二级区域展开图。地图可交互，点击片区查看管网节点。'
  if (level.value === 'zone') return '地图已切换为道路底图。点击小区标记，在右侧展开该小区全部住户。'
  return '右侧住户清单支持按户号、表号、楼栋房号搜索；点击住户查看水表明细。'
})

const navigationUrl = (target) => {
  // Household room numbers are demo fields; navigation intentionally resolves
  // to the verified community location, which is the supported accuracy level.
  const keyword = target?.meterId
    ? selectedCommunity.value?.address
    : (target?.address || `浙江省杭州市${currentDistrict.value?.name || ''}${target?.name || ''}`)
  return `https://uri.amap.com/search?keyword=${encodeURIComponent(keyword)}&city=${encodeURIComponent('杭州市')}&callnative=1`
}

const reset = () => {
  path.value = []
  selectedMeter.value = null
}
const goUp = () => {
  path.value = path.value.slice(0, -1)
  selectedMeter.value = null
}
const jumpTo = (i) => {
  path.value = path.value.slice(0, i + 1)
  selectedMeter.value = null
}
const enterDistrict = (d) => {
  if (!d) return
  path.value = [{ type: 'district', name: d.name, node: d }]
  selectedMeter.value = null
}
const enterByName = (name) => {
  enterDistrict(districtByName.value[name])
}
const onShapeClick = (shape) => {
  enterByName(shape.name)
}
const enterTile = (tile) => {
  selectedMeter.value = null
  if (level.value === 'district') path.value = [...path.value, { type: 'zone', name: tile.name, node: tile }]
  else if (level.value === 'zone') path.value = [...path.value, { type: 'community', name: tile.name, node: tile }]
}
const selectCommunity = (community) => {
  selectedMeter.value = null
  householdQuery.value = ''
  const base = path.value.filter((item) => item.type !== 'community')
  path.value = [...base, { type: 'community', name: community.name, node: community }]
}
const pickMeter = (row) => {
  selectedMeter.value = row
}
</script>

<style scoped lang="scss">
.hz { display: grid; gap: 12px; min-height: calc(100vh - 120px); }
.top {
  display: flex; justify-content: space-between; gap: 20px; align-items: flex-end; padding: 10px 4px 5px;
  .top-copy { min-width: 0; }
  .eyebrow { font-size: 11px; letter-spacing: .16em; text-transform: uppercase; color: #0284c7; margin-bottom: 7px; font-weight: 700; }
  h1 { font-size: clamp(24px, 2.4vw, 32px); line-height: 1.22; margin-bottom: 6px; overflow-wrap: anywhere; }
  .desc { color: var(--color-muted); font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
}
.top-actions { display: flex; flex: 0 0 auto; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.ghost {
  border: 1px solid rgba(8,131,149,.2); background: rgba(255,255,255,.88); border-radius: 999px; padding: 9px 15px; cursor: pointer;
  color: var(--teal-700); font-weight: 600; line-height: 1.2; white-space: nowrap; box-shadow: 0 5px 16px rgba(8,60,80,.05);
  transition: background .2s ease, border-color .2s ease, transform .2s ease, box-shadow .2s ease;
  &:hover { background: #fff; border-color: rgba(8,131,149,.42); transform: translateY(-1px); box-shadow: 0 8px 20px rgba(8,60,80,.1); }
  &:focus-visible { outline: 3px solid rgba(5,191,219,.24); outline-offset: 2px; }
}
.crumb {
  display: flex; flex-wrap: wrap; align-items: center; gap: 7px; min-width: 0; padding: 11px 14px; border-radius: 14px;
  background: rgba(255,255,255,.9); border: 1px solid var(--color-border); box-shadow: 0 8px 24px rgba(8,60,80,.045);
  button { border: none; background: transparent; cursor: pointer; color: #0284c7; font-size: 13px; line-height: 1.45; white-space: normal; overflow-wrap: anywhere; &.on { font-weight: 700; color: var(--color-ink); } }
  i { font-style: normal; color: #9ab; }
  .meta { min-width: 0; margin-left: auto; font-size: 12px; line-height: 1.5; color: var(--color-muted); overflow-wrap: anywhere; text-align: right; }
}
.level-badge {
  flex: 0 0 auto; padding: 4px 9px; border-radius: 999px; color: #036781; background: rgba(5,191,219,.12);
  border: 1px solid rgba(5,191,219,.24); font-size: 11px; font-weight: 700; letter-spacing: .04em;
}
.legend {
  display: flex; flex-wrap: wrap; gap: 8px 18px; padding: 1px 5px;
  .leg { display: inline-flex; align-items: center; gap: 7px; font-size: 12px; line-height: 1.5; color: var(--color-muted); white-space: nowrap; }
  .swatch {
    width: 12px; height: 12px; border-radius: 4px; box-shadow: inset 0 0 0 1px rgba(15,23,42,.08), 0 2px 5px rgba(15,23,42,.08);
    &.ok { background: #0284c7; }
    &.warn { background: #c4a35a; }
    &.danger { background: #e07a5f; }
    &.muted { background: #94adbe; }
  }
}
.stage { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(300px, .82fr); gap: 14px; min-height: 640px; align-items: start; }

.map-panel, .grid-panel, .table-panel, .side-card {
  background: #fff; border: 1px solid var(--color-border); border-radius: 20px;
  box-shadow: 0 14px 36px rgba(8, 60, 80, 0.075);
}
.map-panel {
  position: relative;
  isolation: isolate;
  min-width: 0;
  padding: 28px 18px 36px;
  overflow: hidden;
  background:
    radial-gradient(700px 300px at 12% 0%, rgba(5,191,219,.12), transparent 56%),
    radial-gradient(520px 260px at 100% 100%, rgba(8,131,149,.08), transparent 62%),
    linear-gradient(180deg, #f9fdff 0%, #edf6fa 100%);
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    opacity: .38;
    background-image: radial-gradient(circle at center, rgba(8,131,149,.22) 0 1px, transparent 1.4px);
    background-size: 22px 22px;
    mask-image: linear-gradient(135deg, #000, transparent 68%);
  }
}

/* 桌面感：轻微透视 + 斜放，不做夸张浮空 */
.desk {
  position: relative;
  perspective: 1400px;
  z-index: 1;
  padding: 6px 2.5% 24px;
}
.desk-shadow {
  position: absolute;
  left: 12%;
  right: 10%;
  bottom: 6px;
  height: 28px;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, rgba(11, 36, 48, 0.22), transparent 70%);
  filter: blur(6px);
  pointer-events: none;
}
.map-canvas {
  position: relative;
  width: 100%;
  aspect-ratio: 746 / 640;
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(145deg, #f8fcff, #eef5fa);
  border: 1px solid rgba(2, 132, 199, 0.16);
  box-shadow:
    0 1px 0 rgba(255,255,255,.7) inset,
    0 18px 40px rgba(11, 36, 48, 0.12),
    0 4px 10px rgba(11, 36, 48, 0.06);
  transform: rotateX(7deg) rotateZ(-2deg) translateY(-2px);
  transform-style: preserve-3d;
  transition: transform .28s cubic-bezier(.22,.7,.3,1);
  &:hover {
    transform: rotateX(5deg) rotateZ(-1deg) translateY(-4px);
  }
}
@media (prefers-reduced-motion: reduce) {
  .map-canvas { transition: none; }
  .map-canvas:hover { transform: rotateX(7deg) rotateZ(-2deg) translateY(-2px); }
}
.city-svg {
  position: absolute; inset: 0; width: 100%; height: 100%; z-index: 1;
}
.district-g {
  cursor: pointer;
  transition: opacity .2s ease;
  &.muted { opacity: .72; }
  &.active, &:hover { opacity: 1; }
}
.district-label {
  fill: #0f172a;
  font-size: 13.5px;
  font-weight: 800;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  paint-order: stroke fill;
  stroke: rgba(255,255,255,.9);
  stroke-width: 3px;
  stroke-linejoin: round;
  &.sub-label { font-weight: 700; stroke-width: .75px; }
}
.district-stat {
  fill: rgba(255,255,255,.96); font-weight: 700; paint-order: stroke;
  stroke: rgba(15,23,42,.38); stroke-width: .55px; stroke-linejoin: round;
}

.submap-panel {
  padding: 16px 18px 28px;
  .grid-head.in-map {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    gap: 12px;
    margin-bottom: 10px;
    > div:first-child { min-width: 0; }
    h2 { line-height: 1.35; overflow-wrap: anywhere; }
    p { line-height: 1.55; overflow-wrap: anywhere; }
  }
  .chip-note {
    flex: 0 1 auto;
    font-size: 11px;
    color: #64748b;
    padding: 4px 10px;
    border-radius: 999px;
    background: rgba(2, 132, 199, 0.08);
    border: 1px solid rgba(2, 132, 199, 0.14);
    line-height: 1.45;
    text-align: center;
    overflow-wrap: anywhere;
    &.real { color: #087f5b; background: rgba(23,166,115,.1); border-color: rgba(23,166,115,.2); }
  }
  .map-tools {
    display: grid;
    gap: 8px;
    justify-items: end;
  }
  .zoom-bar {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    input[type="range"] { width: 110px; accent-color: #0284c7; }
    em { font-style: normal; font-size: 12px; color: #64748b; min-width: 52px; }
  }
  .zoom-btn {
    width: 28px; height: 28px; border-radius: 8px; border: 1px solid var(--color-border);
    background: #fff; cursor: pointer; color: #0f172a;
    &:disabled { opacity: .4; cursor: default; }
  }
  .map-canvas.sub {
    aspect-ratio: 4 / 3;
    transform: none;
    transition: none;
    &:hover { transform: none; }
  }
  .mini-table {
    margin-top: 12px;
    border-top: 1px solid var(--color-border);
    padding-top: 10px;
  }
}
.real-map-shell { height: 590px; border: 1px solid rgba(15,118,110,.16); border-radius: 15px; overflow: hidden; box-shadow: 0 16px 34px rgba(15,23,42,.1); }
.nav-link {
  display: inline-flex; align-items: center; justify-content: center; width: fit-content; padding: 7px 11px;
  border-radius: 9px; color: #fff; background: #087f8c; text-decoration: none; font-size: 12px; font-weight: 700;
  line-height: 1.45; text-align: center; overflow-wrap: anywhere;
  transition: background .18s ease, transform .18s ease;
  &:hover { background: #066d78; transform: translateY(-1px); }
  &:focus-visible { outline: 3px solid rgba(5,191,219,.28); outline-offset: 2px; }
  &.full { width: 100%; margin-top: 12px; box-sizing: border-box; }
}
.submap-panel .desk { perspective: none; padding: 8px 2% 12px; }
.submap-panel .desk-shadow { display: none; }
.network-main { stroke: rgba(255,255,255,.82); stroke-width: 2.2; stroke-linecap: round; }
.network-branch { stroke: rgba(230,248,255,.64); stroke-width: 1.05; stroke-dasharray: 3 2; stroke-linecap: round; }
.network-node { fill: #e8faff; stroke: #036781; stroke-width: .75; }
.network-node.main { fill: #05bfdb; stroke: #fff; stroke-width: 1; }
.ring-g {
  cursor: pointer;
  transition: opacity .18s ease;
  &.active, &:hover { opacity: 1; filter: drop-shadow(0 2px 4px rgba(11,36,48,.18)); }
  &.cluster .ring-pct { font-size: 7.5px; }
}
.ring-label {
  fill: #334155;
  font-size: 8.5px;
  font-weight: 700;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  paint-order: stroke fill;
  stroke: rgba(255,255,255,.85);
  stroke-width: 2px;
  pointer-events: none;
}
.ring-pct {
  fill: #0f172a;
  font-size: 7px;
  font-weight: 700;
  font-family: "DIN Alternate", "PingFang SC", sans-serif;
  pointer-events: none;
}

.grid-panel, .table-panel { padding: 18px; }
.grid-head { margin-bottom: 14px; h2 { font-size: 20px; margin-bottom: 4px; } p { color: var(--color-muted); font-size: 13px; } }

.tile-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.status-tile {
  position: relative;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: #fff;
  padding: 0;
  display: flex;
  overflow: hidden;
  cursor: pointer;
  text-align: left;
  transition: transform .2s ease, box-shadow .2s ease;
  &:hover { transform: translateY(-2px); box-shadow: 0 10px 22px rgba(8,80,100,.1); }
  .tile-bar { width: 6px; flex-shrink: 0; }
  &.ok .tile-bar { background: #0284c7; }
  &.warn .tile-bar { background: #c4a35a; }
  &.danger .tile-bar { background: #e07a5f; }
  &.muted .tile-bar { background: #94adbe; }
  .tile-body { padding: 14px 14px 14px 12px; min-width: 0; }
  .tile-top { display: flex; justify-content: space-between; gap: 8px; align-items: center; margin-bottom: 6px; }
  h3 { font-size: 15px; margin: 0; }
  p { font-size: 12px; color: var(--color-muted); line-height: 1.4; margin: 0; }
}
.status-pill {
  font-size: 11px; font-weight: 600; padding: 3px 8px; border-radius: 999px; white-space: nowrap;
  .ok & { background: rgba(2,132,199,.12); color: #0369a1; }
  .warn & { background: rgba(196,163,90,.16); color: #8a6a20; }
  .danger & { background: rgba(224,122,95,.16); color: #b84830; }
  .muted & { background: #eef2f4; color: #789; }
}
.empty-box {
  min-height: 360px; display: grid; place-content: center; justify-items: center; gap: 10px; text-align: center; color: var(--color-muted);
  h3 { color: var(--color-ink); margin-top: 8px; }
}
.empty-chip {
  font-size: 12px; font-weight: 600; padding: 6px 12px; border-radius: 999px;
  &.muted { background: #eef2f4; color: #789; }
}

.side { display: grid; min-width: 0; gap: 12px; align-content: start; }
.side-card { min-width: 0; padding: 17px 18px; }
.side-top {
  display: flex; gap: 14px; align-items: center; margin-bottom: 12px;
  > div:last-child { min-width: 0; }
  h3 { font-size: 18px; line-height: 1.35; margin-bottom: 7px; overflow-wrap: anywhere; }
  .tag {
    display: inline-block; font-size: 12px; padding: 3px 10px; border-radius: 999px;
    &.ok { background: rgba(2,132,199,.12); color: #0369a1; }
    &.warn { background: rgba(196,163,90,.16); color: #8a6a20; }
    &.danger { background: rgba(224,122,95,.16); color: #b84830; }
    &.muted { background: #eef2f4; color: #789; }
  }
}
.status-block {
  flex: 0 0 82px; width: 82px; min-height: 82px; border-radius: 18px; display: grid; place-content: center; text-align: center;
  border: 1px solid transparent;
  strong { font-family: var(--font-display); font-size: clamp(20px, 2vw, 24px); line-height: 1.1; padding-top: 1px; overflow-wrap: anywhere; }
  small { font-size: 11px; color: var(--color-muted); margin-top: 4px; }
  &.ok { background: rgba(2,132,199,.1); border-color: rgba(2,132,199,.18); strong { color: #0284c7; } }
  &.warn { background: rgba(196,163,90,.12); border-color: rgba(196,163,90,.22); strong { color: #8a6a20; } }
  &.danger { background: rgba(224,122,95,.12); border-color: rgba(224,122,95,.22); strong { color: #b84830; } }
  &.muted { background: #eef2f4; border-color: #e2e8ec; strong { color: #789; } }
}
.rate-track {
  height: 6px; border-radius: 999px; background: #eef4f7; overflow: hidden; margin-bottom: 14px;
  i { display: block; height: 100%; border-radius: inherit;
    &.ok { background: #0284c7; }
    &.warn { background: #c4a35a; }
    &.danger { background: #e07a5f; }
    &.muted { background: #94adbe; }
  }
}
.kpis {
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px;
  div { min-width: 0; background: linear-gradient(145deg, #f4fbff, #edf8fc); border: 1px solid rgba(2,132,199,.07); border-radius: 13px; padding: 11px 12px; }
  dt { font-size: 12px; color: var(--color-muted); }
  dd { font-family: var(--font-display); font-size: clamp(18px, 2vw, 22px); line-height: 1.25; margin-top: 3px; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
}
.tip {
  em { font-style: normal; font-size: 12px; color: #0284c7; }
  p { margin: 8px 0 0; line-height: 1.55; font-size: 13px; color: var(--color-muted); }
}
.meter {
  h4 { font-family: var(--font-display); font-size: 20px; line-height: 1.35; overflow-wrap: anywhere; }
  > p { color: var(--color-muted); margin: 4px 0 12px; font-size: 13px; overflow-wrap: anywhere; }
  ul { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
  li { display: flex; justify-content: space-between; gap: 12px; padding: 8px 10px; background: #f7fbfc; border-radius: 10px;
    span { color: var(--color-muted); font-size: 13px; }
    b { min-width: 0; text-align: right; overflow-wrap: anywhere; }
  }
}
.meter .meter-address { margin: -6px 0 12px; line-height: 1.5; }
.households { padding: 0; overflow: hidden; }
.household-head {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 18px 12px;
  border-bottom: 1px solid var(--color-border);
  > div { min-width: 0; }
  h4 { margin-top: 3px; font-size: 17px; line-height: 1.4; overflow-wrap: anywhere; }
  > b { color: #087f8c; font-family: var(--font-display); font-size: 18px; white-space: nowrap; }
}
.side-eyebrow { color: var(--color-muted); font-size: 11px; letter-spacing: .08em; }
.household-search {
  display: flex; align-items: center; gap: 8px; margin: 12px 14px; padding: 0 11px; border: 1px solid #d7e4e9;
  border-radius: 10px; background: #f8fbfc;
  span { color: var(--color-muted); font-size: 11px; }
  input { min-width: 0; width: 100%; height: 36px; border: 0; outline: 0; background: transparent; color: var(--color-ink); }
  &:focus-within { border-color: #0793a2; box-shadow: 0 0 0 3px rgba(7,147,162,.1); }
}
.household-list { max-height: 440px; overflow-y: auto; padding: 0 8px 10px; scrollbar-gutter: stable; }
.household-row {
  width: 100%; display: grid; grid-template-columns: 10px minmax(0,1fr) auto; gap: 9px; align-items: center;
  padding: 10px; border: 0; border-radius: 10px; background: transparent; color: var(--color-ink); cursor: pointer; text-align: left;
  &:hover, &.active { background: #edf8fa; }
  &.active { box-shadow: inset 3px 0 #087f8c; }
  &:focus-visible { outline: 3px solid rgba(5,191,219,.22); outline-offset: -2px; }
}
.household-state { width: 8px; height: 8px; border-radius: 50%; background: #0e9f83; &.bad { background: #d45d45; } }
.household-main {
  min-width: 0; display: grid; gap: 3px;
  strong { font-size: 13px; line-height: 1.4; overflow-wrap: anywhere; }
  small {
    color: var(--color-muted); font-size: 10px; line-height: 1.4; overflow: hidden; overflow-wrap: anywhere;
    display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2;
  }
}
.household-usage { color: #0f6070; font-family: var(--font-display); font-size: 14px; white-space: nowrap; small { margin-left: 2px; font-size: 9px; color: var(--color-muted); } }
.household-empty { padding: 28px 10px; color: var(--color-muted); text-align: center; font-size: 12px; }
.ok { color: #0369a1; } .bad { color: #c45a40; font-weight: 600; }

@media (max-width: 1100px) {
  .stage { grid-template-columns: 1fr; }
  .map-canvas { transform: rotateX(5deg) rotateZ(-1.5deg); }
  .map-canvas:hover { transform: rotateX(4deg) rotateZ(-1deg) translateY(-3px); }
  .side { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .side-card.households, .side-card.meter { grid-column: 1 / -1; }
}
@media (max-width: 760px) {
  .hz { gap: 10px; }
  .top { align-items: flex-start; flex-direction: column; padding-top: 4px; }
  .top-actions { width: 100%; justify-content: flex-start; }
  .top-actions .ghost { flex: 1 1 auto; }
  .crumb { align-items: flex-start; }
  .crumb .meta { flex-basis: 100%; margin-left: 0; text-align: left; }
  .legend { gap: 7px 12px; }
  .stage { min-height: 0; }
  .map-panel { padding: 18px 10px 24px; border-radius: 16px; }
  .desk { padding: 4px 0 12px; }
  .map-canvas { border-radius: 12px; transform: none; }
  .map-canvas:hover { transform: translateY(-2px); }
  .side { grid-template-columns: 1fr; }
  .side-card.households, .side-card.meter { grid-column: auto; }
  .submap-panel .grid-head.in-map { align-items: flex-start; flex-direction: column; }
  .submap-panel .map-tools { justify-items: start; }
  .real-map-shell { height: 460px; }
  .household-list { max-height: 360px; }
}
</style>
