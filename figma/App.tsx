import { useState } from 'react';
import { Home } from './components/Home';
import { AppSelector } from './components/AppSelector';
import { BotSettings } from './components/BotSettings';
import { Guide } from './components/Guide';
import { Home as HomeIcon, Bell, Settings, Info } from 'lucide-react';

type Tab = 'home' | 'apps' | 'settings' | 'guide';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('home');

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-100 p-4">
      {/* Mobile Phone Frame */}
      <div className="w-full max-w-[380px] h-[780px] bg-white rounded-[2.5rem] shadow-2xl overflow-hidden border-8 border-gray-800 relative">
        {/* Status Bar */}
        <div className="bg-gray-900 text-white px-6 py-2 flex items-center justify-between text-xs">
          <span>9:41</span>
          <div className="flex items-center gap-1">
            <span>5G</span>
            <span>●●●●</span>
            <span>100%</span>
          </div>
        </div>

        {/* App Content */}
        <div className="h-[calc(100%-116px)] overflow-y-auto bg-gray-50">
          {activeTab === 'home' && <Home />}
          {activeTab === 'apps' && <AppSelector />}
          {activeTab === 'settings' && <BotSettings />}
          {activeTab === 'guide' && <Guide />}
        </div>

        {/* Bottom Navigation */}
        <div className="absolute bottom-0 left-0 right-0 bg-white border-t border-gray-200 px-2 py-2">
          <div className="flex items-center justify-around">
            <NavButton
              icon={<HomeIcon size={24} />}
              label="홈"
              active={activeTab === 'home'}
              onClick={() => setActiveTab('home')}
            />
            <NavButton
              icon={<Bell size={24} />}
              label="앱 선택"
              active={activeTab === 'apps'}
              onClick={() => setActiveTab('apps')}
            />
            <NavButton
              icon={<Settings size={24} />}
              label="설정"
              active={activeTab === 'settings'}
              onClick={() => setActiveTab('settings')}
            />
            <NavButton
              icon={<Info size={24} />}
              label="가이드"
              active={activeTab === 'guide'}
              onClick={() => setActiveTab('guide')}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function NavButton({ 
  icon, 
  label, 
  active, 
  onClick 
}: { 
  icon: React.ReactNode; 
  label: string; 
  active: boolean; 
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex flex-col items-center gap-1 px-3 py-1 rounded-lg transition-colors ${
        active ? 'text-blue-600' : 'text-gray-500'
      }`}
    >
      {icon}
      <span className="text-[10px]">{label}</span>
    </button>
  );
}