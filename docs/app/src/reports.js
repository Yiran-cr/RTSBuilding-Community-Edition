// 构建时内联 docs/reports/*.json（相对 src/ 为 ../../reports），file:// 打开也可用（无需 fetch）
const modules = import.meta.glob('../../reports/*.json', { eager: true, import: 'default' })

/**
 * 加载全部报告并按 id 排序（保持稳定顺序）。
 * @returns {Array<Object>} 报告对象数组
 */
export function loadReports() {
  const reports = Object.values(modules)
  reports.sort((a, b) => (a.title || '').localeCompare(b.title || '', 'zh-Hans-CN'))
  return reports
}
