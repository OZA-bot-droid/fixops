import { useEffect, useState } from "react";
import { Field } from "../../components/ui";

export default function KaptRegionSelector({ value, onChange, disabled }) {
  const [catalog, setCatalog] = useState(null);
  const [missing, setMissing] = useState(false);
  const [manual, setManual] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    fetch(`${import.meta.env.BASE_URL}data/kapt-regions.json`, { signal: controller.signal })
      .then(r => { if (!r.ok) throw new Error("REGION_CATALOG_UNAVAILABLE"); return r.json(); })
      .then(data => {
        if (!Array.isArray(data.provinces) || !Array.isArray(data.districts) || !Array.isArray(data.towns)
            || !data.provinces.length) throw new Error("REGION_CATALOG_INVALID");
        if (!controller.signal.aborted) setCatalog(data);
      })
      .catch(() => { if (!controller.signal.aborted) { setMissing(true); setManual(true); } });
    return () => controller.abort();
  }, []);
  const provinces = catalog?.provinces || [];
  const province = provinces.some(x => x.code === value.slice(0, 2)) ? value.slice(0, 2) : "";
  const districts = (catalog?.districts || []).filter(x => x.code.startsWith(province) && province);
  const district = districts.some(x => x.code === value.slice(0, 5)) ? value.slice(0, 5) : "";
  const towns = (catalog?.towns || []).filter(x => district && x.code.startsWith(district));
  const town = towns.some(x => x.code === value) ? value : "";
  const unmatched = value && !(value === province || value === district || value === town);
  return <div className="grid gap-3 sm:col-span-2">
    {catalog && !manual && !unmatched && <div className="grid gap-3 sm:grid-cols-3">
      <Field label="시·도" as="select" disabled={disabled} value={province} onChange={e => onChange(e.target.value)}>
        <option value="">전국 (지역 선택 안 함)</option>
        {provinces.map(x => <option key={x.code} value={x.code}>{x.name}</option>)}
      </Field>
      <Field label="시·군·구" as="select" disabled={disabled || !province} value={district} onChange={e => onChange(e.target.value || province)}>
        <option value="">전체 시·군·구</option>
        {districts.map(x => <option key={x.code} value={x.code}>{x.name}</option>)}
      </Field>
      <Field label="법정동·리" as="select" disabled={disabled || !district} value={town} onChange={e => onChange(e.target.value || district)}>
        <option value="">전체 법정동</option>
        {towns.map(x => <option key={x.code} value={x.code}>{x.name}</option>)}
      </Field>
    </div>}
    {(!catalog || manual || unmatched) && <Field label="행정구역 코드" value={value} disabled={disabled} inputMode="numeric" pattern="([0-9]{2}|[0-9]{5}|[0-9]{10})" maxLength={10} onChange={e => onChange(e.target.value.replace(/[^0-9]/g, ""))} hint="시도 2자리 / 시군구 5자리 / 법정동 10자리" />}
    {catalog && <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={manual || Boolean(unmatched)} disabled={disabled} onChange={e => { setManual(e.target.checked); if (!e.target.checked && unmatched) onChange(""); }} /> 코드 직접 입력</label>}
    {missing && <p className="text-xs text-fixops-muted">법정동 코드 데이터가 준비되지 않아 직접 입력으로 전환했습니다.</p>}
    {catalog && <p className="text-xs text-fixops-muted">선택 코드: {value || "전국"} · {catalog.complete ? "공식 자료 기반" : "예시 데이터 (다른 지역은 코드 직접 입력)"}</p>}
  </div>;
}
