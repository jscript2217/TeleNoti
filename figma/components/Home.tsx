import { Bell, CheckCircle, Send } from 'lucide-react';

export function Home() {
  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="text-center space-y-2">
        <div className="w-20 h-20 bg-blue-600 rounded-3xl mx-auto flex items-center justify-center shadow-lg">
          <Send className="text-white" size={40} />
        </div>
        <h1 className="text-gray-900">알림 → 텔레그램</h1>
        <p className="text-gray-500 text-sm">스마트폰 알림을 텔레그램으로 받아보세요</p>
      </div>

      {/* Status Card */}
      <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-gray-900">연결 상태</h2>
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
            <span className="text-green-600 text-sm">활성화</span>
          </div>
        </div>
        
        <div className="space-y-3">
          <div className="flex items-center justify-between py-2 border-b border-gray-100">
            <span className="text-gray-600 text-sm">연결된 앱</span>
            <span className="text-blue-600">12개</span>
          </div>
          <div className="flex items-center justify-between py-2">
            <span className="text-gray-600 text-sm">오늘 전송된 알림</span>
            <span className="text-blue-600">47개</span>
          </div>
        </div>
      </div>

      {/* Quick Actions */}
      <div className="space-y-3">
        <h3 className="text-gray-900 text-sm">빠른 작업</h3>
        <div className="grid grid-cols-2 gap-3">
          <QuickActionCard
            icon={<Bell size={24} />}
            title="앱 선택"
            description="알림 받을 앱"
            color="bg-blue-500"
          />
          <QuickActionCard
            icon={<CheckCircle size={24} />}
            title="테스트 전송"
            description="연결 확인"
            color="bg-green-500"
          />
        </div>
      </div>

      {/* Recent Notifications Preview */}
      <div className="space-y-3">
        <h3 className="text-gray-900 text-sm">최근 알림</h3>
        <div className="bg-white rounded-2xl shadow-md divide-y divide-gray-100">
          <RecentNotification
            app="카카오톡"
            message="새 메시지가 도착했습니다"
            time="2분 전"
            icon="💬"
          />
          <RecentNotification
            app="Gmail"
            message="새 이메일이 있습니다"
            time="15분 전"
            icon="📧"
          />
          <RecentNotification
            app="Instagram"
            message="좋아요 3개"
            time="1시간 전"
            icon="📷"
          />
        </div>
      </div>
    </div>
  );
}

function QuickActionCard({ 
  icon, 
  title, 
  description, 
  color 
}: { 
  icon: React.ReactNode; 
  title: string; 
  description: string; 
  color: string;
}) {
  return (
    <button className="bg-white rounded-2xl shadow-md p-4 text-left hover:shadow-lg transition-shadow">
      <div className={`w-12 h-12 ${color} rounded-xl flex items-center justify-center text-white mb-3`}>
        {icon}
      </div>
      <div>
        <div className="text-gray-900 text-sm mb-1">{title}</div>
        <div className="text-gray-500 text-xs">{description}</div>
      </div>
    </button>
  );
}

function RecentNotification({ 
  app, 
  message, 
  time, 
  icon 
}: { 
  app: string; 
  message: string; 
  time: string;
  icon: string;
}) {
  return (
    <div className="p-4 flex items-start gap-3">
      <div className="text-2xl">{icon}</div>
      <div className="flex-1 min-w-0">
        <div className="text-gray-900 text-sm">{app}</div>
        <div className="text-gray-500 text-xs truncate">{message}</div>
      </div>
      <div className="text-gray-400 text-xs whitespace-nowrap">{time}</div>
    </div>
  );
}