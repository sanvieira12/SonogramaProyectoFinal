import { useSearchParams } from 'react-router-dom'
import VinylFutureTab from './importar/VinylFutureTab'
import DiscogsTab from './importar/DiscogsTab'
import ManualTab from './importar/ManualTab'

const TABS = [
  { key: 'vinylfuture', label: 'VinylFuture' },
  { key: 'discogs',     label: 'Discogs' },
  { key: 'manual',      label: 'Manual' },
]

export default function Importar() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tabParam = searchParams.get('tab')
  const activeTab = TABS.find(t => t.key === tabParam)?.key ?? 'vinylfuture'

  function switchTab(key) {
    setSearchParams({ tab: key })
  }

  return (
    <main className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">Importar discos</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-stone-400">
          Importá discos desde Excel de VinylFuture, desde Discogs, o ingresalos manualmente.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 p-1 bg-slate-100 dark:bg-stone-900 rounded-xl mb-6 w-fit">
        {TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => switchTab(tab.key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              activeTab === tab.key
                ? 'bg-white dark:bg-stone-800 text-slate-900 dark:text-white shadow-sm'
                : 'text-slate-500 dark:text-stone-400 hover:text-slate-700 dark:hover:text-stone-200'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="card p-6">
        {activeTab === 'vinylfuture' && <VinylFutureTab />}
        {activeTab === 'discogs'     && <DiscogsTab />}
        {activeTab === 'manual'      && <ManualTab />}
      </div>
    </main>
  )
}
