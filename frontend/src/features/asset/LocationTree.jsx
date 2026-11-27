import { useState } from "react";
import { StatusBadge } from "../../components/ui";
import { LOCATION_LABELS, locationForest } from "./locationRules";

function Node({ node, selectedId, onSelect, depth = 0 }) {
  const [expanded, setExpanded] = useState(node.locationType !== "BUILDING");
  return <li>
    <div className={`flex items-center gap-2 rounded-lg px-2 py-2 ${String(selectedId) === String(node.id) ? "bg-blue-50" : "hover:bg-fixops-background"}`}>
      {node.children.length > 0 ? <button type="button" className="h-8 w-8 shrink-0" aria-label={node.name + (expanded ? " 접기" : " 펼치기")} aria-expanded={expanded} onClick={() => setExpanded(v => !v)}>{expanded ? "▾" : "▸"}</button> : <span className="w-8 shrink-0" />}
      <button type="button" aria-pressed={String(selectedId) === String(node.id)} className="flex min-w-0 flex-1 flex-wrap items-center justify-between gap-2 text-left" onClick={() => onSelect(node.id)}>
        <span className="min-w-0 break-words"><span className="block text-sm font-semibold">{node.name}</span><span className="text-xs text-fixops-muted">{LOCATION_LABELS[node.locationType] || node.locationType} · {node.code}</span></span>
        <StatusBadge status={node.status} />
      </button>
    </div>
    {expanded && depth < 32 && node.children.length > 0 && <ul className="ml-4 border-l border-fixops-border pl-2">{node.children.map(child => <Node key={child.id} node={child} selectedId={selectedId} onSelect={onSelect} depth={depth + 1} />)}</ul>}
  </li>;
}
export default function LocationTree({ items, selectedId, onSelect }) {
  const roots = locationForest(items);
  return <ul aria-label="위치 계층" className="grid gap-1">{roots.map(node => <Node key={node.id} node={node} selectedId={selectedId} onSelect={onSelect} />)}</ul>;
}
