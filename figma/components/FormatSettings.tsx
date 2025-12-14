import { useState } from 'react';
import { FileText, Copy, RotateCcw, Eye } from 'lucide-react';

const DEFAULT_FORMAT = '📱 {app}\n📌 {title}\n💬 {text}\n🕐 {time}';

const PRESET_FORMATS = [
  {
    name: '기본',
    format: '📱 {app}\n📌 {title}\n💬 {text}\n🕐 {time}',
  },
  {
    name: '간단',
    format: '[{app}] {title}\n{text}',
  },
  {
    name: '상세',
    format: '━━━━━━━━━━━━\n📱 앱: {app}\n📌 제목: {title}\n💬 내용: {text}\n🕐 시간: {time}\n━━━━━━━━━━━━',
  },
  {
    name: '최소',
    format: '{app}: {text}',
  },
];

const VARIABLES = [
  { key: '{app}', description: '앱 이름' },
  { key: '{title}', description: '알림 제목' },
  { key: '{text}', description: '알림 내용' },
  { key: '{time}', description: '시간' },
  { key: '{package}', description: '패키지명' },
];

const SAMPLE_DATA = {
  app: '카카오톡',
  title: '친구 이름',
  text: '안녕하세요! 오늘 저녁 약속 괜찮으신가요?',
  time: '오후 2:30',
  package: 'com.kakao.talk',
};

export function FormatSettings() {
  const [format, setFormat] = useState(DEFAULT_FORMAT);
  const [showPreview, setShowPreview] = useState(true);

  const applyPreset = (presetFormat: string) => {
    setFormat(presetFormat);
  };

  const resetFormat = () => {
    setFormat(DEFAULT_FORMAT);
  };

  const insertVariable = (variable: string) => {
    setFormat(format + variable);
  };

  const getPreview = () => {
    let preview = format;
    preview = preview.replace(/{app}/g, SAMPLE_DATA.app);
    preview = preview.replace(/{title}/g, SAMPLE_DATA.title);
    preview = preview.replace(/{text}/g, SAMPLE_DATA.text);
    preview = preview.replace(/{time}/g, SAMPLE_DATA.time);
    preview = preview.replace(/{package}/g, SAMPLE_DATA.package);
    return preview;
  };

  return (
    <div className="p-6 space-y-6 pb-20">
      {/* Header */}
      <div className="text-center space-y-2">
        <div className="w-16 h-16 bg-purple-600 rounded-2xl mx-auto flex items-center justify-center">
          <FileText className="text-white" size={32} />
        </div>
        <h1 className="text-gray-900">알림 형식 설정</h1>
        <p className="text-gray-500 text-sm">텔레그램으로 전송될 메시지 형식 설정</p>
      </div>

      {/* Preset Templates */}
      <div className="space-y-3">
        <h3 className="text-gray-900 text-sm">프리셋 템플릿</h3>
        <div className="grid grid-cols-2 gap-2">
          {PRESET_FORMATS.map((preset, index) => (
            <button
              key={index}
              onClick={() => applyPreset(preset.format)}
              className="p-3 bg-white rounded-xl shadow-md hover:shadow-lg transition-shadow border-2 border-transparent hover:border-purple-500"
            >
              <div className="text-gray-900 text-sm mb-1">{preset.name}</div>
              <div className="text-gray-400 text-xs truncate">{preset.format.split('\n')[0]}</div>
            </button>
          ))}
        </div>
      </div>

      {/* Format Editor */}
      <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-gray-900 text-sm">메시지 형식</h3>
          <button
            onClick={resetFormat}
            className="flex items-center gap-1 px-3 py-1.5 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors text-xs text-gray-700"
          >
            <RotateCcw size={14} />
            초기화
          </button>
        </div>
        <textarea
          value={format}
          onChange={(e) => setFormat(e.target.value)}
          placeholder="메시지 형식을 입력하세요..."
          className="w-full px-4 py-3 bg-gray-50 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm min-h-[120px] font-mono"
        />
        <p className="text-gray-400 text-xs">
          변수를 사용하여 동적으로 정보를 표시할 수 있습니다
        </p>
      </div>

      {/* Variables */}
      <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
        <h3 className="text-gray-900 text-sm">사용 가능한 변수</h3>
        <div className="space-y-2">
          {VARIABLES.map((variable, index) => (
            <button
              key={index}
              onClick={() => insertVariable(variable.key)}
              className="w-full flex items-center justify-between p-3 bg-gray-50 rounded-xl hover:bg-purple-50 transition-colors group"
            >
              <div className="text-left">
                <div className="text-purple-700 text-sm font-mono">{variable.key}</div>
                <div className="text-gray-500 text-xs">{variable.description}</div>
              </div>
              <Copy size={16} className="text-gray-400 group-hover:text-purple-600" />
            </button>
          ))}
        </div>
      </div>

      {/* Preview Toggle */}
      <div className="bg-white rounded-2xl shadow-md p-5 space-y-4">
        <button
          onClick={() => setShowPreview(!showPreview)}
          className="w-full flex items-center justify-between"
        >
          <div className="flex items-center gap-2">
            <Eye size={20} className="text-gray-600" />
            <h3 className="text-gray-900 text-sm">미리보기</h3>
          </div>
          <div
            className={`w-12 h-6 rounded-full transition-colors relative ${
              showPreview ? 'bg-purple-600' : 'bg-gray-300'
            }`}
          >
            <div
              className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                showPreview ? 'translate-x-7' : 'translate-x-1'
              }`}
            />
          </div>
        </button>

        {showPreview && (
          <div className="pt-3 border-t border-gray-100">
            <div className="text-gray-500 text-xs mb-2">예시 알림 (카카오톡)</div>
            <div className="bg-gray-50 rounded-xl p-4 border border-gray-200">
              <pre className="text-gray-900 text-sm whitespace-pre-wrap break-words font-sans">
                {getPreview()}
              </pre>
            </div>
          </div>
        )}
      </div>

      {/* Tips */}
      <div className="bg-purple-50 rounded-2xl p-5 space-y-3">
        <h3 className="text-purple-900 text-sm">💡 팁</h3>
        <div className="space-y-2 text-xs text-purple-800">
          <div className="flex gap-2">
            <span className="flex-shrink-0">•</span>
            <span>이모지를 사용하여 메시지를 더 알아보기 쉽게 만들 수 있습니다</span>
          </div>
          <div className="flex gap-2">
            <span className="flex-shrink-0">•</span>
            <span>줄바꿈을 사용하여 정보를 구분하세요</span>
          </div>
          <div className="flex gap-2">
            <span className="flex-shrink-0">•</span>
            <span>변수를 클릭하면 자동으로 에디터에 추가됩니다</span>
          </div>
          <div className="flex gap-2">
            <span className="flex-shrink-0">•</span>
            <span>미리보기에서 실제 메시지 형태를 확인하세요</span>
          </div>
        </div>
      </div>

      {/* Save Button */}
      <button className="w-full py-4 bg-purple-600 hover:bg-purple-700 text-white rounded-xl transition-colors shadow-md">
        형식 저장
      </button>
    </div>
  );
}
