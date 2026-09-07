import source from './hangzhouHierarchy.js'
import { locationFor } from './hangzhouLocations.js'

// The CSV supplied with the demo covers the eight central districts.  The map,
// however, contains all thirteen Hangzhou districts.  Complete the uncovered
// districts with deterministic demo records so every map area can be explored.
const districtCatalog = {
  '临安区': [
    ['锦城片区', ['锦城街道', '锦北街道']],
    ['青山湖片区', ['青山湖街道', '玲珑街道']],
    ['西部片区', ['於潜镇', '昌化镇']]
  ],
  '富阳区': [
    ['富春片区', ['富春街道', '鹿山街道']],
    ['银湖片区', ['银湖街道', '东洲街道']],
    ['新登片区', ['新登镇', '场口镇']]
  ],
  '桐庐县': [
    ['桐君片区', ['桐君街道', '城南街道']],
    ['富春江片区', ['富春江镇', '横村镇']],
    ['分水片区', ['分水镇', '瑶琳镇']]
  ],
  '淳安县': [
    ['千岛湖片区', ['千岛湖镇', '界首乡']],
    ['汾口片区', ['汾口镇', '大墅镇']],
    ['威坪片区', ['威坪镇', '姜家镇']]
  ],
  '建德市': [
    ['新安江片区', ['新安江街道', '洋溪街道']],
    ['梅城片区', ['梅城镇', '下涯镇']],
    ['寿昌片区', ['寿昌镇', '大同镇']]
  ]
}

const round = (value, digits = 1) => Number(Number(value).toFixed(digits))

const makeMeter = (district, zone, community, districtIndex, zoneIndex, communityIndex, meterIndex) => {
  const seed = (districtIndex + 3) * 1009 + (zoneIndex + 5) * 137 + (communityIndex + 7) * 53 + meterIndex * 29
  const alarm = seed % 23 === 0 || seed % 41 === 0
  const pressure = alarm
    ? (seed % 2 ? 0.16 + (seed % 5) * 0.01 : 0.48 + (seed % 6) * 0.01)
    : 0.29 + (seed % 10) * 0.01
  const usage = alarm ? 0.16 + (seed % 11) * 0.012 : 0.018 + (seed % 15) * 0.006
  const id = 10001 + districtIndex * 1000 + zoneIndex * 100 + communityIndex * 40 + meterIndex
  return {
    meterId: `HZ${String(id).padStart(6, '0')}`,
    city: '杭州',
    district,
    zone,
    community,
    timestamp: `2026-08-${String(14 + (seed % 8)).padStart(2, '0')} ${String(seed % 24).padStart(2, '0')}:00`,
    waterUsage: round(usage, 3),
    flowRate: round(usage * (72 + seed % 19), 1),
    pressure: round(pressure, 2),
    temperature: round(27 + (seed % 65) / 10, 1),
    status: alarm ? '异常' : '正常',
    alarm: alarm ? 1 : 0
  }
}

const summarize = (meters) => {
  const alarmCount = meters.reduce((sum, meter) => sum + meter.alarm, 0)
  const usage = meters.reduce((sum, meter) => sum + meter.waterUsage, 0)
  return {
    meters: meters.length,
    alarmCount,
    alarmRate: meters.length ? round(alarmCount / meters.length * 100, 1) : 0,
    usage: round(usage, 3)
  }
}

const makeDistrict = (district, zoneSpecs, districtIndex, layout) => {
  const zones = zoneSpecs.map(([zoneName, communityNames], zoneIndex) => {
    const communities = communityNames.map((communityName, communityIndex) => {
      const metersDetail = Array.from({ length: 32 }, (_, meterIndex) =>
        makeMeter(district, zoneName, communityName, districtIndex, zoneIndex, communityIndex, meterIndex)
      )
      const stats = summarize(metersDetail)
      return {
        name: communityName,
        ...stats,
        share: 50,
        avgPressure: round(metersDetail.reduce((sum, meter) => sum + meter.pressure, 0) / metersDetail.length, 3),
        avgFlow: round(metersDetail.reduce((sum, meter) => sum + meter.flowRate, 0) / metersDetail.length, 2),
        metersDetail
      }
    })
    const meterList = communities.flatMap((community) => community.metersDetail)
    return {
      name: zoneName,
      ...summarize(meterList),
      share: round(100 / zoneSpecs.length, 1),
      communities
    }
  })
  const meters = zones.flatMap((zone) => zone.communities).flatMap((community) => community.metersDetail)
  return {
    name: district,
    ...summarize(meters),
    share: 0,
    hasData: true,
    sampleSource: '系统补全演示样本',
    layout,
    zones
  }
}

const districts = source.districts.map((district, districtIndex) => {
  if (district.hasData && district.zones?.length) return { ...district, sampleSource: 'CSV样本' }
  const specs = districtCatalog[district.name]
  return specs ? makeDistrict(district.name, specs, districtIndex, district.layout) : district
})

// Add real-map anchors and non-personal demo household fields to both the CSV
// records and generated records.  Position accuracy intentionally stops at the
// community boundary; individual homes are not assigned fake GPS coordinates.
districts.forEach((district) => {
  let communityIndex = 0
  district.zones.forEach((zone) => {
    zone.communities.forEach((community) => {
      const location = locationFor(district.name, community.name, communityIndex++)
      community.location = location
      community.address = `浙江省杭州市${district.name}${community.name}`
      community.metersDetail.forEach((meter, meterIndex) => {
        const building = meterIndex % 18 + 1
        const unit = meterIndex % 3 + 1
        const room = (Math.floor(meterIndex / 3) % 18 + 1) * 100 + (meterIndex % 4 + 1)
        meter.householdNo = `${district.name.slice(0, 2)}-${String(meterIndex + 1).padStart(4, '0')}`
        meter.householdName = `示范住户 ${String(meterIndex + 1).padStart(3, '0')}`
        meter.address = `${community.address}${building}幢${unit}单元${room}室`
        meter.lat = location.lat
        meter.lng = location.lng
      })
    })
  })
})

const totalMeters = districts.reduce((sum, district) => sum + (district.meters || 0), 0)
const totalAlarms = districts.reduce((sum, district) => sum + (district.alarmCount || 0), 0)

districts.forEach((district) => {
  district.share = totalMeters ? round(district.meters / totalMeters * 100, 1) : 0
})

export default {
  ...source,
  totalMeters,
  totalAlarms,
  updatedAt: '2026-08-21 16:00',
  dataCoverage: `${districts.filter((district) => district.hasData).length}/${districts.length}`,
  districts
}
