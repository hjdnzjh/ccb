/**
 * 二次划分几何 + 空心圆环锚点 + 比例尺聚合
 */

export function pathBBox(d) {
  if (!d) return null
  const nums = String(d).match(/-?\d*\.?\d+(?:e[-+]?\d+)?/gi)
  if (!nums || nums.length < 4) return null
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (let i = 0; i + 1 < nums.length; i += 2) {
    const x = Number(nums[i])
    const y = Number(nums[i + 1])
    if (!Number.isFinite(x) || !Number.isFinite(y)) continue
    minX = Math.min(minX, x)
    minY = Math.min(minY, y)
    maxX = Math.max(maxX, x)
    maxY = Math.max(maxY, y)
  }
  if (!Number.isFinite(minX)) return null
  return { minX, minY, maxX, maxY, w: maxX - minX, h: maxY - minY }
}

function hashName(name) {
  let h = 0
  const s = String(name || '')
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return h
}

function gridLayout(n) {
  if (n <= 1) return { cols: 1, rows: 1 }
  if (n === 2) return { cols: 2, rows: 1 }
  if (n === 3) return { cols: 3, rows: 1 }
  if (n === 4) return { cols: 2, rows: 2 }
  const cols = Math.ceil(Math.sqrt(n))
  const rows = Math.ceil(n / cols)
  return { cols, rows }
}

/**
 * 无白缝拼接地图片区：单元格贴合 bbox，仅保留发丝分割线。
 */
export function partitionBBox(bbox, items) {
  if (!bbox || !items?.length) return []
  const { minX, minY, w, h } = bbox
  const { cols, rows } = gridLayout(items.length)
  const cellW = w / cols
  const cellH = h / rows
  const out = []

  items.forEach((item, i) => {
    const col = i % cols
    const row = Math.floor(i / cols)
    const left = minX + col * cellW
    const top = minY + row * cellH
    // 末行/末列贴边，消除缝隙
    const right = col === cols - 1 ? minX + w : minX + (col + 1) * cellW
    const bottom = row === rows - 1 ? minY + h : minY + (row + 1) * cellH
    const mx = (left + right) / 2
    const my = (top + bottom) / 2
    const d = `M ${left.toFixed(2)} ${top.toFixed(2)} L ${right.toFixed(2)} ${top.toFixed(2)} L ${right.toFixed(2)} ${bottom.toFixed(2)} L ${left.toFixed(2)} ${bottom.toFixed(2)} Z`
    out.push({
      ...item,
      d,
      cx: mx,
      cy: my,
      bbox: { minX: left, minY: top, maxX: right, maxY: bottom, w: right - left, h: bottom - top }
    })
  })
  return out
}

/**
 * 将完整区县轮廓切成放射状的二级地图区域。
 * 实际显示时由区县 path clip，因此结果是放大的矢量地图，而不是矩形卡片或底图图片。
 */
export function partitionExpandedMap(bbox, items) {
  if (!bbox || !items?.length) return []
  const { minX, minY, maxX, maxY, w, h } = bbox
  const radius = Math.hypot(w, h) * 1.35
  const centerSeed = hashName(items.map((item) => item.name).join('|'))
  const cx = minX + w * (0.48 + ((centerSeed % 7) - 3) * 0.008)
  const cy = minY + h * (0.5 + (((centerSeed >> 3) % 7) - 3) * 0.008)
  const startAngle = -Math.PI / 2 - Math.PI / items.length * 0.35
  const slice = Math.PI * 2 / items.length

  return items.map((item, index) => {
    const a0 = startAngle + index * slice
    const a1 = a0 + slice
    const points = [[cx, cy]]
    const segments = 12
    for (let step = 0; step <= segments; step++) {
      const angle = a0 + (a1 - a0) * (step / segments)
      points.push([cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius])
    }
    const mid = (a0 + a1) / 2
    const labelRadius = Math.min(w, h) * (items.length <= 2 ? 0.23 : 0.3)
    const labelX = Math.max(minX + w * 0.15, Math.min(maxX - w * 0.15, cx + Math.cos(mid) * labelRadius))
    const labelY = Math.max(minY + h * 0.15, Math.min(maxY - h * 0.15, cy + Math.sin(mid) * labelRadius))
    const d = `${points.map(([x, y], pointIndex) => `${pointIndex ? 'L' : 'M'} ${x.toFixed(2)} ${y.toFixed(2)}`).join(' ')} Z`
    return {
      ...item,
      d,
      cx: labelX,
      cy: labelY,
      bbox: { minX, minY, maxX, maxY, w, h }
    }
  })
}

/** 在区域内铺空心圆环锚点（示意坐标，稳定可复现） */
export function placeRings(bbox, items, inset = 0.12) {
  if (!bbox || !items?.length) return []
  const { minX, minY, w, h } = bbox
  const ix = w * inset
  const iy = h * inset
  const x0 = minX + ix
  const y0 = minY + iy
  const aw = Math.max(1, w - ix * 2)
  const ah = Math.max(1, h - iy * 2)
  const { cols, rows } = gridLayout(items.length)
  const cellW = aw / cols
  const cellH = ah / rows

  return items.map((item, i) => {
    const col = i % cols
    const row = Math.floor(i / cols)
    const jitterX = (((hashName(item.name) % 21) - 10) / 100) * cellW
    const jitterY = ((((hashName(item.name) >> 4) % 21) - 10) / 100) * cellH
    const cx = x0 + (col + 0.5) * cellW + jitterX
    const cy = y0 + (row + 0.5) * cellH + jitterY
    const meters = Number(item.node?.meters || item.meters || 0)
    const alarmCount = Number(item.node?.alarmCount || item.alarmCount || 0)
    const alarmRate = meters
      ? Number(((alarmCount / meters) * 100).toFixed(1))
      : Number(item.node?.alarmRate || item.alarmRate || 0)
    return {
      ...item,
      id: item.name || item.id || `r-${i}`,
      cx,
      cy,
      meters,
      alarmCount,
      alarmRate,
      count: 1,
      members: [item]
    }
  })
}

/**
 * 小比例尺网格聚合：合并附近圆环并重算异常占比。
 * cellSize 越大（缩得越远）聚合越强。
 */
export function clusterRings(rings, cellSize) {
  if (!rings?.length) return []
  if (!cellSize || cellSize <= 0) return rings.map((r) => ({ ...r, clustered: false }))

  const buckets = new Map()
  rings.forEach((r) => {
    const gx = Math.floor(r.cx / cellSize)
    const gy = Math.floor(r.cy / cellSize)
    const key = `${gx}:${gy}`
    if (!buckets.has(key)) buckets.set(key, [])
    buckets.get(key).push(r)
  })

  const out = []
  buckets.forEach((list) => {
    if (list.length === 1) {
      out.push({ ...list[0], clustered: false, count: 1, members: list })
      return
    }
    const meters = list.reduce((s, r) => s + (r.meters || 0), 0)
    const alarmCount = list.reduce((s, r) => s + (r.alarmCount || 0), 0)
    const cx = list.reduce((s, r) => s + r.cx, 0) / list.length
    const cy = list.reduce((s, r) => s + r.cy, 0) / list.length
    const alarmRate = meters ? Number(((alarmCount / meters) * 100).toFixed(1)) : 0
    out.push({
      id: `cluster-${list.map((r) => r.id).join('-').slice(0, 48)}`,
      name: `${list.length} 处合并`,
      cx,
      cy,
      meters,
      alarmCount,
      alarmRate,
      count: list.length,
      clustered: true,
      members: list,
      node: null
    })
  })
  return out
}

export function fitViewBox(bbox, paddingRatio = 0.12) {
  if (!bbox) return '0 0 100 100'
  const padX = bbox.w * paddingRatio
  const padY = bbox.h * paddingRatio
  const x = bbox.minX - padX
  const y = bbox.minY - padY
  const w = bbox.w + padX * 2
  const h = bbox.h + padY * 2
  return `${x} ${y} ${w} ${h}`
}

/** zoom 1=最远(强聚合) … 5=最近(几乎不聚合) */
export function clusterCellSize(bbox, zoom) {
  if (!bbox) return 0
  const base = Math.max(bbox.w, bbox.h)
  const z = Math.max(1, Math.min(5, Number(zoom) || 1))
  // zoom1 ≈ 22% 边长一格；zoom5 ≈ 4%（基本不合并）
  const ratio = 0.26 - (z - 1) * 0.055
  return base * Math.max(0.04, ratio)
}

export function ringRadius(bbox, count = 1, clustered = false) {
  const base = Math.max(bbox?.w || 40, bbox?.h || 40)
  const r = base * (clustered ? 0.045 : 0.032)
  return Math.max(4.5, Math.min(22, r * (1 + Math.log10(Math.max(1, count)))))
}
