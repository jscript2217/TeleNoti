import { Bot, Bell, Smartphone, Settings, CheckCircle, ExternalLink } from 'lucide-react';

export function Guide() {
  return (
    <div className="p-6 space-y-6 pb-20">
      {/* Header */}
      <div className="text-center space-y-2">
        <h1 className="text-gray-900">사용 가이드</h1>
        <p className="text-gray-500 text-sm">앱을 설정하고 사용하는 방법</p>
      </div>

      {/* Setup Steps */}
      <div className="space-y-4">
        <h2 className="text-gray-900 text-sm">초기 설정</h2>
        
        <SetupStep
          number={1}
          icon={<Bot size={24} className="text-blue-600" />}
          title="텔레그램 봇 생성"
          description="텔레그램에서 @BotFather를 검색하고 /newbot 명령어로 새 봇을 만드세요. 봇 토큰을 받게 됩니다."
        />

        <SetupStep
          number={2}
          icon={<Settings size={24} className="text-blue-600" />}
          title="봇 정보 입력"
          description="'설정' 탭에서 받은 봇 토큰과 Chat ID를 입력하세요. @userinfobot에게 메시지를 보내면 Chat ID를 확인할 수 있습니다."
        />

        <SetupStep
          number={3}
          icon={<Bell size={24} className="text-blue-600" />}
          title="알림 권한 허용"
          description="앱이 알림을 읽을 수 있도록 안드로이드 설정에서 알림 접근 권한을 허용하세요."
        />

        <SetupStep
          number={4}
          icon={<CheckCircle size={24} className="text-blue-600" />}
          title="앱 선택"
          description="'앱 선택' 탭에서 텔레그램으로 알림을 받고 싶은 앱들을 선택하세요."
        />
      </div>

      {/* Features */}
      <div className="space-y-4">
        <h2 className="text-gray-900 text-sm">주요 기능</h2>
        
        <FeatureCard
          icon="📱"
          title="실시간 알림 전달"
          description="선택한 앱의 알림을 실시간으로 텔레그램 봇으로 전송합니다."
        />

        <FeatureCard
          icon="🔔"
          title="앱별 관리"
          description="앱별로 알림 수신 여부를 개별적으로 설정할 수 있습니다."
        />

        <FeatureCard
          icon="📊"
          title="통계 확인"
          description="전송된 알림 개수와 앱별 통계를 확인할 수 있습니다."
        />

        <FeatureCard
          icon="📲"
          title="멀티 디바이스"
          description="여러 기기를 등록하여 한 번에 관리할 수 있습니다."
        />
      </div>

      {/* FAQ */}
      <div className="space-y-4">
        <h2 className="text-gray-900 text-sm">자주 묻는 질문</h2>
        
        <FAQItem
          question="알림이 전송되지 않아요"
          answer="알림 접근 권한이 허용되어 있는지, 봇 토큰과 Chat ID가 올바른지 확인하세요. 배터리 최적화 설정도 확인해보세요."
        />

        <FAQItem
          question="Chat ID는 어떻게 얻나요?"
          answer="텔레그램에서 @userinfobot을 검색하여 메시지를 보내면 Chat ID를 알려줍니다."
        />

        <FAQItem
          question="여러 기기에서 사용 가능한가요?"
          answer="네, 동일한 봇 정보를 입력하면 여러 기기에서 사용할 수 있습니다. '기기' 탭에서 관리하세요."
        />

        <FAQItem
          question="개인정보는 안전한가요?"
          answer="모든 알림은 귀하의 텔레그램 봇으로만 전송되며, 외부 서버에 저장되지 않습니다."
        />
      </div>

      {/* Support */}
      <div className="bg-blue-50 rounded-2xl p-5 space-y-3">
        <h3 className="text-blue-900 text-sm">도움이 필요하신가요?</h3>
        <p className="text-blue-800 text-xs">
          더 많은 정보와 지원이 필요하시면 아래 링크를 확인하세요.
        </p>
        <div className="space-y-2">
          <SupportLink label="텔레그램 봇 API 문서" />
          <SupportLink label="이메일 문의하기" />
          <SupportLink label="GitHub 저장소" />
        </div>
      </div>

      {/* Version */}
      <div className="text-center text-gray-400 text-xs">
        버전 1.0.0
      </div>
    </div>
  );
}

function SetupStep({ 
  number, 
  icon, 
  title, 
  description 
}: { 
  number: number;
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="bg-white rounded-2xl shadow-md p-5 flex gap-4">
      <div className="flex flex-col items-center gap-2 flex-shrink-0">
        <div className="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center text-white text-sm">
          {number}
        </div>
        <div className="w-12 h-12 bg-blue-50 rounded-xl flex items-center justify-center">
          {icon}
        </div>
      </div>
      <div className="flex-1">
        <h3 className="text-gray-900 text-sm mb-2">{title}</h3>
        <p className="text-gray-600 text-xs leading-relaxed">{description}</p>
      </div>
    </div>
  );
}

function FeatureCard({ 
  icon, 
  title, 
  description 
}: { 
  icon: string;
  title: string;
  description: string;
}) {
  return (
    <div className="bg-white rounded-2xl shadow-md p-5 flex gap-4">
      <div className="text-3xl flex-shrink-0">{icon}</div>
      <div className="flex-1">
        <h3 className="text-gray-900 text-sm mb-1">{title}</h3>
        <p className="text-gray-600 text-xs">{description}</p>
      </div>
    </div>
  );
}

function FAQItem({ 
  question, 
  answer 
}: { 
  question: string;
  answer: string;
}) {
  return (
    <div className="bg-white rounded-2xl shadow-md p-5 space-y-2">
      <h3 className="text-gray-900 text-sm">Q. {question}</h3>
      <p className="text-gray-600 text-xs leading-relaxed">A. {answer}</p>
    </div>
  );
}

function SupportLink({ label }: { label: string }) {
  return (
    <button className="w-full py-2 px-4 bg-white rounded-lg hover:bg-blue-100 transition-colors flex items-center justify-between">
      <span className="text-blue-900 text-xs">{label}</span>
      <ExternalLink size={14} className="text-blue-600" />
    </button>
  );
}
