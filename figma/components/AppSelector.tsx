import { useState } from 'react';
import { Search, CheckCircle, Circle } from 'lucide-react';

interface App {
  id: string;
  name: string;
  icon: string;
  package: string;
  enabled: boolean;
}

const MOCK_APPS: App[] = [
  { id: '1', name: '카카오톡', icon: '💬', package: 'com.kakao.talk', enabled: true },
  { id: '2', name: 'Gmail', icon: '📧', package: 'com.google.android.gm', enabled: true },
  { id: '3', name: 'Instagram', icon: '📷', package: 'com.instagram.android', enabled: true },
  { id: '4', name: 'WhatsApp', icon: '💚', package: 'com.whatsapp', enabled: false },
  { id: '5', name: 'Slack', icon: '💼', package: 'com.slack', enabled: true },
  { id: '6', name: 'Discord', icon: '🎮', package: 'com.discord', enabled: true },
  { id: '7', name: 'Twitter', icon: '🐦', package: 'com.twitter.android', enabled: false },
  { id: '8', name: 'Facebook', icon: '👥', package: 'com.facebook.katana', enabled: false },
  { id: '9', name: 'YouTube', icon: '📺', package: 'com.google.android.youtube', enabled: true },
  { id: '10', name: 'Spotify', icon: '🎵', package: 'com.spotify.music', enabled: false },
  { id: '11', name: 'Netflix', icon: '🎬', package: 'com.netflix.mediaclient', enabled: false },
  { id: '12', name: 'Telegram', icon: '✈️', package: 'org.telegram.messenger', enabled: true },
  { id: '13', name: 'Line', icon: '💚', package: 'jp.naver.line.android', enabled: false },
  { id: '14', name: 'Tinder', icon: '🔥', package: 'com.tinder', enabled: false },
  { id: '15', name: 'LinkedIn', icon: '💼', package: 'com.linkedin.android', enabled: false },
];

export function AppSelector() {
  const [apps, setApps] = useState<App[]>(MOCK_APPS);
  const [searchQuery, setSearchQuery] = useState('');

  const toggleApp = (id: string) => {
    setApps(apps.map(app => 
      app.id === id ? { ...app, enabled: !app.enabled } : app
    ));
  };

  const filteredApps = apps.filter(app =>
    app.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    app.package.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const enabledCount = apps.filter(app => app.enabled).length;

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="bg-white px-6 py-5 space-y-4 border-b border-gray-200">
        <div>
          <h1 className="text-gray-900">알림 받을 앱 선택</h1>
          <p className="text-gray-500 text-sm mt-1">
            {enabledCount}개 앱 활성화됨
          </p>
        </div>

        {/* Search Bar */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={20} />
          <input
            type="text"
            placeholder="앱 검색..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-3 bg-gray-100 rounded-xl border-none focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* Quick Filters */}
        <div className="flex gap-2">
          <FilterButton label="전체 선택" onClick={() => setApps(apps.map(a => ({ ...a, enabled: true })))} />
          <FilterButton label="전체 해제" onClick={() => setApps(apps.map(a => ({ ...a, enabled: false })))} />
        </div>
      </div>

      {/* App List */}
      <div className="flex-1 overflow-y-auto">
        {filteredApps.length === 0 ? (
          <div className="flex items-center justify-center h-full text-gray-400">
            검색 결과가 없습니다
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {filteredApps.map(app => (
              <AppItem
                key={app.id}
                app={app}
                onToggle={() => toggleApp(app.id)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function FilterButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="px-4 py-2 bg-gray-100 rounded-lg text-gray-700 text-xs hover:bg-gray-200 transition-colors"
    >
      {label}
    </button>
  );
}

function AppItem({ app, onToggle }: { app: App; onToggle: () => void }) {
  return (
    <button
      onClick={onToggle}
      className="w-full px-6 py-4 flex items-center gap-4 hover:bg-gray-50 transition-colors"
    >
      {/* App Icon */}
      <div className="w-12 h-12 rounded-xl bg-gray-100 flex items-center justify-center text-2xl flex-shrink-0">
        {app.icon}
      </div>

      {/* App Info */}
      <div className="flex-1 text-left min-w-0">
        <div className="text-gray-900 text-sm">{app.name}</div>
        <div className="text-gray-400 text-xs truncate">{app.package}</div>
      </div>

      {/* Checkbox */}
      <div className="flex-shrink-0">
        {app.enabled ? (
          <CheckCircle className="text-blue-600" size={24} />
        ) : (
          <Circle className="text-gray-300" size={24} />
        )}
      </div>
    </button>
  );
}
