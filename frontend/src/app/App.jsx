import { Link, Route, Routes } from 'react-router-dom';
const views = ['member', 'asset', 'workorder', 'inventory', 'ai'];
export default function App() {
  return <main className="min-h-screen bg-fixops-background text-fixops-text p-8"><div className="mx-auto max-w-5xl space-y-5">
    <header><h1 className="text-3xl font-semibold text-fixops-primary">FixOps</h1><p>MSA 개발 기본 틀 · 아직 인증·서비스 연동 전입니다.</p></header>
    <nav className="flex flex-wrap gap-3">{views.map(x=><Link className="rounded-lg border px-4 py-2" key={x} to={'/'+x}>{x}</Link>)}</nav>
    <Routes><Route path="/" element={<p>팀원별 개발 화면은 /member, /asset, /workorder, /inventory, /ai에 연결합니다.</p>}/>
      {views.map(x=><Route key={x} path={'/'+x} element={<section className="rounded-xl border p-5">{x} placeholder · 담당자 구현 예정</section>}/>)}
    </Routes></div></main>;
}
