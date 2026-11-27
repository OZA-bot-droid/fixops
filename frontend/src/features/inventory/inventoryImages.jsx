import { useEffect, useRef, useState } from "react";
import { fetchPartImage, fetchPartRequestImage } from "./inventoryApi";

/**
 * 사진 첨부 (부품 요청: 요청할 때 1장 / 부품 등록: 관리자가 등록할 때 1장)
 *
 * 왜 화면에서 사진을 줄이나
 *   휴대폰 사진은 한 장에 3~10MB 라 그대로 보내면 느리고, 화면 서버(nginx)의 기본 업로드 한도(1MB)도 넘는다.
 *   그래서 보내기 전에 긴 변 1280px · JPEG 로 줄인다 (약 200~700KB). 부품 확인용으로는 충분한 화질.
 *
 * 왜 <img src="/api/..."> 로 바로 못 쓰나
 *   사진 API 도 로그인(JWT)이 필요한데, img 태그는 Authorization 헤더를 붙일 수 없다.
 *   그래서 axios 로 받아(blob) 브라우저 안의 임시 주소(blob:)로 바꿔 보여 준다.
 */
export const MAX_IMAGES = 1;
const MAX_SIDE = 1280;
const TARGET_BYTES = 700 * 1024;   // 본문과 합쳐도 1MB 아래
const ACCEPT = ["image/jpeg", "image/png", "image/webp"];

// 사진 한 장을 줄여서 JPEG File 로 돌려준다
async function shrink(file) {
  const bitmap = await createImageBitmap(file);
  let scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  let quality = 0.8;
  for (let i = 0; i < 6; i += 1) {
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#fff";                       // PNG 투명 부분이 검게 나오지 않게
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise((ok) => canvas.toBlob(ok, "image/jpeg", quality));
    if (blob.size <= TARGET_BYTES || i === 5) {
      bitmap.close?.();
      const name = file.name.replace(/\.[^.]+$/, "") + ".jpg";
      return new File([blob], name, { type: "image/jpeg" });
    }
    if (quality > 0.6) quality -= 0.1; else scale *= 0.8;   // 화질 먼저, 그다음 크기를 줄인다
  }
  return file;
}

/** 양식의 사진 고르기: 미리보기 + 지우기 */
export function ImagePicker({ files, onChange, onError, disabled,
  label = `사진 첨부 (선택, ${MAX_IMAGES}장)`, hint = "고장 부위나 부품 라벨 사진을 붙이면 관리자가 확인하기 쉽습니다." }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const previews = usePreviewUrls(files);

  async function pick(e) {
    const chosen = Array.from(e.target.files || []);
    e.target.value = "";                          // 같은 파일을 다시 골라도 동작하게
    if (!chosen.length) return;
    if (chosen.some((f) => !ACCEPT.includes(f.type))) return onError("JPEG, PNG, WEBP 사진만 첨부할 수 있습니다.");
    if (files.length + chosen.length > MAX_IMAGES) return onError(`사진은 ${MAX_IMAGES}장만 첨부할 수 있습니다. 기존 사진을 빼고 다시 골라 주세요.`);
    setBusy(true);
    try {
      const small = await Promise.all(chosen.map(shrink));
      onChange([...files, ...small]);
      onError("");
    } catch {
      onError("사진을 읽지 못했습니다. 다른 사진을 골라 주세요.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <p className="mb-2 text-[13px] font-semibold">{label}</p>
      <div className="flex flex-wrap gap-2">
        {previews.map((url, i) => (
          <div key={url} className="relative h-20 w-20 overflow-hidden rounded-lg border border-fixops-border">
            <img src={url} alt={`첨부 사진 ${i + 1}`} className="h-full w-full object-cover" />
            <button type="button" aria-label={`사진 ${i + 1} 빼기`} disabled={disabled}
              onClick={() => onChange(files.filter((_, j) => j !== i))}
              className="absolute right-1 top-1 grid h-5 w-5 place-items-center rounded-full bg-slate-900/70 text-[12px] text-white">×</button>
          </div>
        ))}
        {files.length < MAX_IMAGES && (
          <button type="button" disabled={disabled || busy} onClick={() => input.current?.click()}
            className="grid h-20 w-20 place-items-center rounded-lg border border-dashed border-fixops-border text-[12px] text-fixops-muted hover:bg-fixops-background">
            {busy ? "줄이는 중…" : "+ 사진"}
          </button>
        )}
      </div>
      {/* capture 를 주지 않아 휴대폰에서는 "카메라 / 앨범" 중 고를 수 있다 */}
      <input ref={input} type="file" accept={ACCEPT.join(",")} hidden onChange={pick} />
      {hint && <p className="mt-1 text-[11px] text-fixops-muted">{hint}</p>}
    </div>
  );
}

// 고른 File 들의 미리보기 주소 (다 쓰면 메모리에서 지운다)
function usePreviewUrls(files) {
  const [urls, setUrls] = useState([]);
  useEffect(() => {
    const made = files.map((f) => URL.createObjectURL(f));
    setUrls(made);
    return () => made.forEach((u) => URL.revokeObjectURL(u));
  }, [files]);
  return urls;
}

/**
 * 로그인 토큰을 붙여 받아 오는 사진.
 *   load    : (signal) => Promise<Blob>   cacheKey 가 바뀔 때만 다시 받는다
 *   onMissing: 사진이 없을 때(404) 부르는 함수. 주면 "불러오기 실패" 대신 아무것도 그리지 않는다
 */
function AuthImage({ load, cacheKey, className, alt, onMissing }) {
  const { url, failed } = useAuthBlob(load, cacheKey, onMissing);
  if (failed && onMissing) return null;
  if (failed) return <div className={`${className} grid place-items-center bg-fixops-background text-[10px] text-fixops-muted`}>불러오기 실패</div>;
  if (!url) return <div className={`${className} animate-pulse bg-fixops-background`} />;
  return <img src={url} alt={alt} className={className} />;
}

// 로그인 토큰을 붙여 사진을 받아 브라우저 임시 주소(blob:)로 바꾼다. 다 쓰면 메모리에서 지운다
function useAuthBlob(load, cacheKey, onMissing) {
  const [state, setState] = useState({ url: null, type: "", failed: false });
  useEffect(() => {
    const ctrl = new AbortController();
    let made = null;
    setState({ url: null, type: "", failed: false });
    load(ctrl.signal)
      .then((blob) => { made = URL.createObjectURL(blob); setState({ url: made, type: blob.type, failed: false }); })
      .catch((err) => {
        if (ctrl.signal.aborted) return;
        if (err?.response?.status === 404 && onMissing) onMissing();
        setState({ url: null, type: "", failed: true });
      });
    return () => { ctrl.abort(); if (made) URL.revokeObjectURL(made); };
  }, [cacheKey]); // eslint-disable-line react-hooks/exhaustive-deps
  return state;
}

const EXT = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };

/**
 * 사진 크게 보기 창. 오른쪽 위에 [다운로드] [×]
 *   공용 Modal 은 머리 부분에 버튼을 넣을 자리가 없어서 (공용 파일은 그대로 두고) 여기서 같은 모양으로 만든다.
 *   다운로드는 이미 받아 둔 사진(blob:)을 그대로 저장하므로 서버에 다시 요청하지 않는다.
 *   fileName: 저장할 이름. 확장자가 없으면 사진 형식에 맞춰 붙인다
 */
function ImageViewer({ open, onClose, title, description, load, cacheKey, fileName }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div role="presentation" className="fixed inset-0 z-50 grid place-items-center bg-slate-900/55 p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section role="dialog" aria-modal="true" aria-label={title} className="w-full max-w-[640px] rounded-2xl bg-white p-5 shadow-2xl sm:p-7">
        <ViewerBody title={title} description={description} load={load} cacheKey={cacheKey} fileName={fileName} onClose={onClose} />
      </section>
    </div>
  );
}

// 창이 열릴 때만 사진을 받도록 본문을 따로 둔다
function ViewerBody({ title, description, load, cacheKey, fileName, onClose }) {
  const { url, type, failed } = useAuthBlob(load, cacheKey);
  const ext = EXT[type] || "jpg";
  const name = /\.[a-z0-9]{2,5}$/i.test(fileName || "") ? fileName : `${fileName || "image"}.${ext}`;
  return (
    <>
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-xl font-bold">{title}</h2>
          {description && <p className="mt-2 truncate text-sm text-fixops-muted">{description}</p>}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {url ? (
            <a href={url} download={name}
              className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-fixops-border px-3 text-[13px] font-semibold hover:bg-fixops-background">
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M12 3v12m0 0-4-4m4 4 4-4M4 17v3h16v-3" />
              </svg>
              다운로드
            </a>
          ) : (
            <span className="inline-flex h-9 items-center rounded-lg border border-fixops-border px-3 text-[13px] font-semibold text-fixops-muted opacity-50">다운로드</span>
          )}
          <button type="button" onClick={onClose} aria-label="닫기" className="grid h-9 w-9 place-items-center text-2xl text-fixops-muted">×</button>
        </div>
      </header>
      <div className="my-5 border-t border-fixops-border" />
      {failed ? <p className="p-8 text-center text-fixops-muted">사진을 불러오지 못했습니다.</p>
        : url ? <img src={url} alt={description || title} className="max-h-[70vh] w-full rounded-lg object-contain" />
        : <div className="aspect-video w-full animate-pulse rounded-lg bg-fixops-background" />}
    </>
  );
}

/** 요청 목록에 보이는 작은 사진들. 누르면 크게 본다 */
export function RequestImageThumbs({ request }) {
  const [open, setOpen] = useState(null);
  const images = request.images || [];
  if (!images.length) return null;
  return (
    <>
      <div className="mt-2 flex gap-1.5">
        {images.map((img, i) => (
          <button key={img.imageId} type="button" onClick={() => setOpen(img)} aria-label={`첨부 사진 ${i + 1} 크게 보기`}
            className="h-10 w-10 overflow-hidden rounded-md border border-fixops-border">
            <AuthImage load={(s) => fetchPartRequestImage(request.requestId, img.imageId, s)} cacheKey={`${request.requestId}-${img.imageId}`}
              alt={img.fileName} className="h-full w-full object-cover" />
          </button>
        ))}
      </div>
      <ImageViewer open={!!open} onClose={() => setOpen(null)} title="첨부 사진" description={open?.fileName}
        load={(s) => fetchPartRequestImage(request.requestId, open.imageId, s)} cacheKey={`${request.requestId}-${open?.imageId}-big`}
        fileName={open ? `부품요청${request.requestId}_${open.fileName}` : ""} />
    </>
  );
}

/** 부품 사진 (part.hasImage 가 false 면 아무것도 그리지 않는다). 누르면 크게 본다 */
export function PartImage({ part, className = "h-16 w-16" }) {
  const [missing, setMissing] = useState(false);
  const [open, setOpen] = useState(false);
  if (!part.hasImage || missing) return null;
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} aria-label="부품 사진 크게 보기"
        className={`${className} shrink-0 overflow-hidden rounded-lg border border-fixops-border`}>
        <AuthImage load={(s) => fetchPartImage(part.id, s)} cacheKey={`part-${part.id}`} onMissing={() => setMissing(true)}
          alt={`${part.name} 사진`} className="h-full w-full object-cover" />
      </button>
      <ImageViewer open={open} onClose={() => setOpen(false)} title="부품 사진" description={`${part.sku} · ${part.name}`}
        load={(s) => fetchPartImage(part.id, s)} cacheKey={`part-${part.id}-big`} fileName={part.sku} />
    </>
  );
}

/**
 * 상품 카드용 정사각형 사진 (쇼핑몰 검색 결과처럼 카드 위쪽을 채운다).
 * 사진이 없으면 회색 바탕에 부품 아이콘. 누르는 동작은 카드가 맡는다 (여기서는 크게 보기 없음)
 */
export function PartCover({ part }) {
  const [missing, setMissing] = useState(false);
  return (
    <div className="relative aspect-square w-full overflow-hidden bg-fixops-background">
      {part.hasImage && !missing ? (
        <AuthImage load={(s) => fetchPartImage(part.id, s)} cacheKey={`part-${part.id}`} onMissing={() => setMissing(true)}
          alt={`${part.name} 사진`} className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105" />
      ) : (
        <div className="grid h-full w-full place-items-center text-fixops-muted">
          <div className="text-center">
            <svg viewBox="0 0 24 24" className="mx-auto h-10 w-10 opacity-40" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
              <path d="M21 8 12 3 3 8v8l9 5 9-5V8Z" /><path d="m3 8 9 5 9-5M12 13v8" />
            </svg>
            <p className="mt-1 text-[11px]">사진 없음</p>
          </div>
        </div>
      )}
    </div>
  );
}
