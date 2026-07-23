import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export default function RiskChart({ labels, values, summary }: { labels: string[]; values: number[]; summary: string }) {
  const data = labels.map((name, index) => ({ name, value: values[index] ?? 0 }))
  return <div className="risk-chart" role="img" aria-label={summary}>
    <ResponsiveContainer width="100%" height="100%"><BarChart data={data} margin={{ top: 12, right: 8, left: -24, bottom: 0 }}><defs><linearGradient id="riskBars" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#39dec0" /><stop offset="1" stopColor="#177c78" /></linearGradient></defs><CartesianGrid stroke="#20384a" strokeDasharray="3 4" vertical={false} /><XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: '#8ba4b6', fontSize: 12 }} /><YAxis allowDecimals={false} axisLine={false} tickLine={false} tick={{ fill: '#647f92', fontSize: 11 }} /><Tooltip cursor={{ fill: 'rgba(48, 215, 184, .05)' }} contentStyle={{ background: '#102331', border: '1px solid #2a4658', borderRadius: 10, color: '#e9f3f7' }} /><Bar dataKey="value" fill="url(#riskBars)" radius={[5, 5, 2, 2]} maxBarSize={44} /></BarChart></ResponsiveContainer>
  </div>
}
