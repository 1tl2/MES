import request from '@/utils/request'
import { createDashboardData, type DashboardData, type DashboardFilters } from '@/mock/dashboard'

function normalizeDashboardData(data: Partial<DashboardData> | null | undefined, filters: DashboardFilters): DashboardData {
  const fallback = createDashboardData(filters)
  if (!data) return fallback

  return {
    filters: data.filters || filters,
    updatedAt: data.updatedAt || fallback.updatedAt,
    kpis: Array.isArray(data.kpis) && data.kpis.length ? data.kpis : fallback.kpis,
    hourlyOutput: Array.isArray(data.hourlyOutput) && data.hourlyOutput.length ? data.hourlyOutput : fallback.hourlyOutput,
    workOrderStatus: Array.isArray(data.workOrderStatus) && data.workOrderStatus.length ? data.workOrderStatus : fallback.workOrderStatus,
    materialReadinessRate: typeof data.materialReadinessRate === 'number' ? data.materialReadinessRate : fallback.materialReadinessRate,
    materialShortages: Array.isArray(data.materialShortages) && data.materialShortages.length ? data.materialShortages : fallback.materialShortages,
    flowNodes: Array.isArray(data.flowNodes) && data.flowNodes.length ? data.flowNodes : fallback.flowNodes,
    workOrders: Array.isArray(data.workOrders) && data.workOrders.length ? data.workOrders : fallback.workOrders,
    qualityTrend: Array.isArray(data.qualityTrend) && data.qualityTrend.length ? data.qualityTrend : fallback.qualityTrend,
    defectRanks: Array.isArray(data.defectRanks) && data.defectRanks.length ? data.defectRanks : fallback.defectRanks,
    inventory: data.inventory || fallback.inventory,
    alerts: Array.isArray(data.alerts) && data.alerts.length ? data.alerts : fallback.alerts,
  }
}

export async function getProductionDashboard(filters: DashboardFilters): Promise<DashboardData> {
  const params: Record<string, string> = {}
  if (filters.date) params.date = filters.date
  if (filters.shift) params.shift = filters.shift
  if (filters.line) params.line = filters.line

  try {
    const data = await request.get<DashboardData>('/dashboard/production', { params })
    return normalizeDashboardData(data, filters)
  } catch (error) {
    console.warn('生产全景看板接口暂不可用，已使用本地演示数据兜底。', error)
    return createDashboardData(filters)
  }
}