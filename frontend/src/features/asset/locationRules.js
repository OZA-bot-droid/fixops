export const LOCATION_LABELS = Object.freeze({
  COMPLEX: "공동주택 단지", BUILDING: "동", UNIT: "세대", COMMON_AREA: "공용 공간",
  FACTORY: "공장", AREA: "작업 구역", LINE: "생산 라인", EQUIPMENT_LOCATION: "설비 설치 위치",
});
export const CHILD_TYPES = Object.freeze({
  COMPLEX: ["BUILDING", "COMMON_AREA"],
  BUILDING: ["UNIT", "COMMON_AREA"],
  FACTORY: ["AREA"],
  AREA: ["LINE"],
  LINE: ["EQUIPMENT_LOCATION"],
  UNIT: [], COMMON_AREA: [], EQUIPMENT_LOCATION: [],
});
export function allowedTypes(kind, parent) {
  if (parent) return parent.status === "ACTIVE" ? CHILD_TYPES[parent.locationType] || [] : [];
  return kind === "BUILDING" ? ["COMPLEX"] : kind === "FACTORY" ? ["FACTORY"] : [];
}
const locationCollator = new Intl.Collator("ko", { numeric: true, sensitivity: "base" });
const compareText = (a, b) => locationCollator.compare(String(a ?? ""), String(b ?? ""))
  || String(a ?? "").localeCompare(String(b ?? ""), "ko");
const locationRank = type => type === "BUILDING" || type === "UNIT" ? 0 : type === "COMMON_AREA" ? 1 : 0;

function sortLocationSiblings(nodes) {
  // Keep unrelated factory/root siblings in their original order.
  if (nodes.some(node => ["BUILDING", "UNIT", "COMMON_AREA"].includes(node.locationType))) {
    nodes.sort((a, b) => locationRank(a.locationType) - locationRank(b.locationType)
      || compareText(a.code, b.code)
      || compareText(a.name, b.name)
      || compareText(a.id, b.id));
  }
  for (const node of nodes) sortLocationSiblings(node.children);
}

export function locationForest(items) {
  const nodes = new Map(items.map(item => [String(item.id), { ...item, children: [] }]));
  const roots = [];
  for (const node of nodes.values()) {
    const parent = nodes.get(String(node.parentId));
    if (!parent || node.parentId == null) { roots.push(node); continue; }
    const seen = new Set([String(node.id)]);
    let cursor = parent;
    while (cursor && !seen.has(String(cursor.id))) {
      seen.add(String(cursor.id));
      cursor = cursor.parentId == null ? null : nodes.get(String(cursor.parentId));
    }
    if (cursor) roots.push(node);
    else parent.children.push(node);
  }
  sortLocationSiblings(roots);
  return roots;
}
