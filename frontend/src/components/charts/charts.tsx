import { Area, Bar, BarChart, CartesianGrid, Cell, ComposedChart, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis, AreaChart } from 'recharts';
import type { AllocationSlice, DayCount, IndexPoint, PricePoint, SeriesPoint } from '../../api/types';
import { fmtCompact, fmtDate, fmtDateTime, fmtMoney, fmtNumber, fmtPercent } from '../../lib/format';

export const PALETTE = ['#34d399', '#38bdf8', '#a78bfa', '#fbbf24', '#f472b6', '#fb923c', '#2dd4bf', '#818cf8', '#f87171', '#94a3b8'];
const GRID = '#1a2540';
const AXIS = { stroke: '#6b7a99', fontSize: 11, tickLine: false, axisLine: false } as const;

interface TipProps {
  active?: boolean;
  payload?: ReadonlyArray<{ name?: string | number; value?: number | string; color?: string }>;
  label?: string | number;
  format: (v: number) => string;
  formatLabel?: (l: string) => string;
}

function ChartTooltip({ active, payload, label, format, formatLabel }: TipProps) {
  if (!active || !payload || payload.length === 0) return null;
  return (
    <div className="rounded-xl border border-ink-600 bg-ink-900/95 px-3 py-2 text-xs shadow-xl">
      <div className="mb-1 text-ink-300">{formatLabel ? formatLabel(String(label)) : String(label)}</div>
      {payload.map((p, i) => (
        <div key={`${p.name}-${i}`} className="flex items-center justify-between gap-4">
          <span className="flex items-center gap-1.5 text-ink-200"><i className="inline-block h-2 w-2 rounded-full" style={{ background: p.color }} />{p.name}</span>
          <span className="tabular font-medium text-white">{typeof p.value === 'number' ? format(p.value) : p.value}</span>
        </div>
      ))}
    </div>
  );
}

const shortDate = (d: string) => fmtDate(d).replace(/,? \d{4}$/, '');

/** Market value against cost basis over time. */
export function ValueChart({ series, currency }: { series: SeriesPoint[]; currency: string }) {
  return (
    <div className="h-64 w-full sm:h-72" role="img" aria-label="Portfolio value and cost basis over time">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={series} margin={{ top: 8, right: 4, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="valueFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#34d399" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#34d399" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="date" tickFormatter={shortDate} minTickGap={40} {...AXIS} />
          <YAxis tickFormatter={(v: number) => fmtCompact(v)} width={48} {...AXIS} />
          <Tooltip content={<ChartTooltip format={(v) => fmtMoney(v, currency)} formatLabel={fmtDate} />} />
          <Area type="monotone" dataKey="marketValue" name="Market value" stroke="#34d399" strokeWidth={2} fill="url(#valueFill)" />
          <Line type="monotone" dataKey="costBasis" name="Cost basis" stroke="#94a3b8" strokeDasharray="4 4" strokeWidth={1.5} dot={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

export interface IndexLine { name: string; color: string; data: IndexPoint[] }

/** Several series rebased to 100, for portfolio and benchmark comparison. */
export function IndexChart({ lines }: { lines: IndexLine[] }) {
  const rows = new Map<string, Record<string, number | string>>();
  for (const line of lines) {
    for (const p of line.data) {
      const row = rows.get(p.date) ?? { date: p.date };
      row[line.name] = p.index;
      rows.set(p.date, row);
    }
  }
  const data = Array.from(rows.values()).sort((a, b) => String(a.date).localeCompare(String(b.date)));
  return (
    <div className="h-64 w-full sm:h-72" role="img" aria-label="Performance comparison, rebased to 100">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 4, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="date" tickFormatter={shortDate} minTickGap={40} {...AXIS} />
          <YAxis domain={['auto', 'auto']} tickFormatter={(v: number) => fmtNumber(v, 0)} width={40} {...AXIS} />
          <Tooltip content={<ChartTooltip format={(v) => fmtNumber(v, 2)} formatLabel={fmtDate} />} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {lines.map((l) => <Line key={l.name} type="monotone" dataKey={l.name} stroke={l.color} strokeWidth={2} dot={false} connectNulls />)}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Price history for one asset. Short ranges include the time of day. */
export function PriceChart({ points, currency = 'USD', intraday }: { points: PricePoint[]; currency?: string; intraday?: boolean }) {
  const up = points.length > 1 && points[points.length - 1].price >= points[0].price;
  const color = up ? '#34d399' : '#f87171';
  const fmtX = (t: string) => (intraday ? new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : shortDate(t));
  return (
    <div className="h-60 w-full sm:h-72" role="img" aria-label="Price history">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={points} margin={{ top: 8, right: 4, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity={0.3} />
              <stop offset="100%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="time" tickFormatter={fmtX} minTickGap={40} {...AXIS} />
          <YAxis domain={['auto', 'auto']} tickFormatter={(v: number) => fmtNumber(v, 0)} width={44} {...AXIS} />
          <Tooltip content={<ChartTooltip format={(v) => fmtMoney(v, currency)} formatLabel={fmtDateTime} />} />
          <Area type="monotone" dataKey="price" name="Price" stroke={color} strokeWidth={2} fill="url(#priceFill)" />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Donut plus an accessible legend that works as a table on small screens. */
export function AllocationDonut({ slices, currency }: { slices: AllocationSlice[]; currency: string }) {
  const data = slices.filter((s) => s.value > 0);
  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-center">
      <div className="h-44 w-44 shrink-0" role="img" aria-label="Allocation by holding">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="value" nameKey="label" innerRadius={52} outerRadius={80} paddingAngle={2} stroke="none">
              {data.map((s, i) => <Cell key={s.label} fill={PALETTE[i % PALETTE.length]} />)}
            </Pie>
            <Tooltip content={<ChartTooltip format={(v) => fmtMoney(v, currency)} formatLabel={(l) => l} />} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="w-full min-w-0 flex-1 space-y-1.5 text-sm">
        {data.map((s, i) => (
          <li key={s.label} className="flex items-center justify-between gap-3">
            <span className="flex min-w-0 items-center gap-2"><i className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: PALETTE[i % PALETTE.length] }} /><span className="truncate text-ink-100">{s.label}</span></span>
            <span className="tabular shrink-0 text-ink-300">{fmtPercent(s.weightPercent)}{s.valuedAtCost ? ' *' : ''}</span>
          </li>
        ))}
        {data.some((s) => s.valuedAtCost) && <li className="pt-1 text-xs text-ink-400">* shown at cost (no current price)</li>}
      </ul>
    </div>
  );
}

/** Counts per day as bars (administrator overview). */
export function DayBars({ data, label, color = '#34d399' }: { data: DayCount[]; label: string; color?: string }) {
  return (
    <div className="h-48 w-full" role="img" aria-label={label}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 4, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="date" tickFormatter={shortDate} minTickGap={32} {...AXIS} />
          <YAxis allowDecimals={false} width={32} {...AXIS} />
          <Tooltip content={<ChartTooltip format={(v) => fmtNumber(v, 0)} formatLabel={fmtDate} />} />
          <Bar dataKey="count" name={label} fill={color} radius={[3, 3, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
