import { useState } from 'react';
import { Save, Eye, EyeOff, Copy, CheckCircle, Bot, Shield, Battery, Play, Pause, AlertCircle } from 'lucide-react';

export function BotSettings() {
  const [botToken, setBotToken] = useState('1234567890:ABCdefGHIjklMNOpqrsTUVwxyz');
  const [chatId, setChatId] = useState('123456789');
  const [showToken, setShowToken] = useState(false);
  const [saved, setSaved] = useState(false);
  const [serviceRunning, setServiceRunning] = useState(true);
  const [notificationPermission, setNotificationPermission] = useState(false);
  const [batteryOptimization, setBatteryOptimization] = useState(false);

  const handleSave = () => {
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const requestNotificationPermission = () => {
    // 실제 앱에서는 안드로이드 설정으로 이동
    setNotificationPermission(true);
  };

  const requestBatteryException = () => {
    // 실제 앱에서는 배터리 최적화 설정으로 이동
    setBatteryOptimization(true);
  };

  const toggleService = () => {
    setServiceRunning(!serviceRunning);
  };

  return (
      <div className="p-6 space-y-6 pb-20">
        {/* Header */}
        <div className="text-center space-y-2">
          <div className="w-16 h-16 bg-blue-600 rounded-2xl mx-auto flex items-center justify-center">
            <Bot className="text-white" size={32} />
          </div>
          <h1 className="text-gray-900">설정</h1>
          <p className="text-gray-500 text-sm">봇 정보 및 권한 관리</p>
        </div>

        {/* Permissions & Service Status */}
        <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
          <h3 className="text-gray-900 text-sm">권한 상태</h3>
          <div className="space-y-3">
            <PermissionStatus
                icon={<Shield size={20} />}
                label="알림 접근 권한"
                granted={notificationPermission}
                onClick={requestNotificationPermission}
            />
            <PermissionStatus
                icon={<Battery size={20} />}
                label="배터리 최적화 예외"
                granted={batteryOptimization}
                onClick={requestBatteryException}
            />
            <div className="pt-2 border-t border-gray-100">
              <button
                  onClick={toggleService}
                  className={`w-full py-3 px-4 rounded-xl flex items-center justify-between transition-colors ${
                      serviceRunning
                          ? 'bg-green-50 hover:bg-green-100'
                          : 'bg-gray-50 hover:bg-gray-100'
                  }`}
              >
                <div className="flex items-center gap-3">
                  {serviceRunning ? (
                      <div className="w-10 h-10 bg-green-500 rounded-xl flex items-center justify-center">
                        <Pause className="text-white" size={20} />
                      </div>
                  ) : (
                      <div className="w-10 h-10 bg-gray-400 rounded-xl flex items-center justify-center">
                        <Play className="text-white" size={20} />
                      </div>
                  )}
                  <div className="text-left">
                    <div className="text-gray-900 text-sm">
                      서비스 {serviceRunning ? '실행 중' : '중지됨'}
                    </div>
                    <div className="text-gray-500 text-xs">
                      {serviceRunning ? '알림 전송이 활성화되어 있습니다' : '알림 전송이 중지되어 있습니다'}
                    </div>
                  </div>
                </div>
                <div
                    className={`w-12 h-6 rounded-full transition-colors relative ${
                        serviceRunning ? 'bg-green-500' : 'bg-gray-300'
                    }`}
                >
                  <div
                      className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                          serviceRunning ? 'translate-x-7' : 'translate-x-1'
                      }`}
                  />
                </div>
              </button>
            </div>
          </div>
        </div>

        {/* Warning if permissions not granted */}
        {(!notificationPermission || !batteryOptimization) && (
            <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4 flex gap-3">
              <AlertCircle className="text-amber-600 flex-shrink-0" size={20} />
              <div className="text-xs text-amber-800">
                <div className="mb-1">필수 권한이 허용되지 않았습니다</div>
                <div className="text-amber-700">
                  앱이 정상적으로 작동하려면 알림 접근 권한과 배터리 최적화 예외가 필요합니다.
                </div>
              </div>
            </div>
        )}

        {/* Bot Token Input */}
        <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
          <div className="space-y-2">
            <label className="text-gray-700 text-sm">봇 토큰 (Bot Token)</label>
            <div className="relative">
              <input
                  type={showToken ? 'text' : 'password'}
                  value={botToken}
                  onChange={(e) => setBotToken(e.target.value)}
                  placeholder="1234567890:ABCdefGHIjklMNOpqrsTUVwxyz"
                  className="w-full px-4 py-3 pr-24 bg-gray-50 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
              />
              <div className="absolute right-2 top-1/2 -translate-y-1/2 flex gap-1">
                <button
                    onClick={() => setShowToken(!showToken)}
                    className="p-2 hover:bg-gray-200 rounded-lg transition-colors"
                >
                  {showToken ? (
                      <EyeOff size={18} className="text-gray-600" />
                  ) : (
                      <Eye size={18} className="text-gray-600" />
                  )}
                </button>
                <button
                    onClick={() => copyToClipboard(botToken)}
                    className="p-2 hover:bg-gray-200 rounded-lg transition-colors"
                >
                  <Copy size={18} className="text-gray-600" />
                </button>
              </div>
            </div>
            <p className="text-gray-400 text-xs">
              @BotFather에서 받은 봇 토큰을 입력하세요
            </p>
          </div>
        </div>

        {/* Chat ID Input */}
        <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
          <div className="space-y-2">
            <label className="text-gray-700 text-sm">Chat ID</label>
            <div className="relative">
              <input
                  type="text"
                  value={chatId}
                  onChange={(e) => setChatId(e.target.value)}
                  placeholder="123456789"
                  className="w-full px-4 py-3 pr-12 bg-gray-50 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
              />
              <button
                  onClick={() => copyToClipboard(chatId)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-2 hover:bg-gray-200 rounded-lg transition-colors"
              >
                <Copy size={18} className="text-gray-600" />
              </button>
            </div>
            <p className="text-gray-400 text-xs">
              알림을 받을 텔레그램 채팅방 ID를 입력하세요
            </p>
          </div>
        </div>

        {/* How to Get Info */}
        <div className="bg-blue-50 rounded-2xl p-5 space-y-3">
          <h3 className="text-blue-900 text-sm">📚 정보를 얻는 방법</h3>
          <div className="space-y-2 text-xs text-blue-800">
            <div className="flex gap-2">
              <span className="flex-shrink-0">1.</span>
              <span>텔레그램에서 @BotFather를 검색하고 /newbot 명령어로 새 봇 생성</span>
            </div>
            <div className="flex gap-2">
              <span className="flex-shrink-0">2.</span>
              <span>봇 토큰을 복사하여 위 필드에 입력</span>
            </div>
            <div className="flex gap-2">
              <span className="flex-shrink-0">3.</span>
              <span>@userinfobot에게 메시지를 보내 Chat ID 확인</span>
            </div>
            <div className="flex gap-2">
              <span className="flex-shrink-0">4.</span>
              <span>받은 Chat ID를 위 필드에 입력</span>
            </div>
          </div>
        </div>

        {/* Test Connection Button */}
        <button className="w-full py-4 bg-green-500 hover:bg-green-600 text-white rounded-xl transition-colors shadow-md">
          테스트 메시지 보내기
        </button>

        {/* Save Button */}
        <button
            onClick={handleSave}
            disabled={saved}
            className={`w-full py-4 rounded-xl transition-all shadow-md flex items-center justify-center gap-2 ${
                saved
                    ? 'bg-green-500 text-white'
                    : 'bg-blue-600 hover:bg-blue-700 text-white'
            }`}
        >
          {saved ? (
              <>
                <CheckCircle size={20} />
                <span>저장 완료!</span>
              </>
          ) : (
              <>
                <Save size={20} />
                <span>설정 저장</span>
              </>
          )}
        </button>

        {/* Additional Settings */}
        <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
          <h3 className="text-gray-900 text-sm">추가 설정</h3>
          <div className="space-y-3">
            <ToggleSetting
                label="백그라운드 유지"
                description="앱이 종료되어도 알림 전송 유지"
            />
            <ToggleSetting
                label="알림 그룹화"
                description="같은 앱의 알림을 그룹으로 전송"
            />
          </div>
        </div>
      </div>
  );
}

function PermissionStatus({
                            icon,
                            label,
                            granted,
                            onClick
                          }: {
  icon: React.ReactNode;
  label: string;
  granted: boolean;
  onClick: () => void;
}) {
  return (
      <button
          onClick={onClick}
          className="w-full flex items-center justify-between py-2"
      >
        <div className="text-left">
          <div className="text-gray-900 text-sm">{label}</div>
          <div className="text-gray-500 text-xs">
            {granted ? '허용됨' : '허용되지 않음'}
          </div>
        </div>
        <div
            className={`w-12 h-6 rounded-full transition-colors relative ${
                granted ? 'bg-blue-600' : 'bg-gray-300'
            }`}
        >
          <div
              className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                  granted ? 'translate-x-7' : 'translate-x-1'
              }`}
          />
        </div>
      </button>
  );
}

function ToggleSetting({
                         label,
                         description
                       }: {
  label: string;
  description: string;
}) {
  const [enabled, setEnabled] = useState(false);

  return (
      <button
          onClick={() => setEnabled(!enabled)}
          className="w-full flex items-center justify-between py-2"
      >
        <div className="text-left">
          <div className="text-gray-900 text-sm">{label}</div>
          <div className="text-gray-500 text-xs">{description}</div>
        </div>
        <div
            className={`w-12 h-6 rounded-full transition-colors relative ${
                enabled ? 'bg-blue-600' : 'bg-gray-300'
            }`}
        >
          <div
              className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                  enabled ? 'translate-x-7' : 'translate-x-1'
              }`}
          />
        </div>
      </button>
  );
}