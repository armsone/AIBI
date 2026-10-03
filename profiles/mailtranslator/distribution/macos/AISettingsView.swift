import SwiftUI

// 설정 › 외부 AI: 웹 계정 로그인 상태, 로그인 창, 브라우저 표시 방식, 전송 동의, 세션 삭제, 진단 로그 공유.

struct AISettingsView: View {
    @State private var accounts = AIBIAccounts.shared
    @ObservedObject private var diagnostics = AIBIDiagnosticsStore.shared
    @State private var confirmClear = false

    var body: some View {
        Form {
            Section {
                ForEach(AIProvider.allCases) { provider in
                    HStack {
                        Text(provider.title).frame(width: 80, alignment: .leading)
                        statusLabel(accounts.statuses[provider])
                        Spacer()
                        Button(accounts.statuses[provider] == .authenticated ? "로그인 화면 열기" : "로그인…") {
                            accounts.openLogin(provider)
                        }
                        .disabled(accounts.isClearing)
                    }
                }
                HStack {
                    Spacer()
                    Button("상태 다시 확인") { accounts.refreshAll() }
                        .disabled(accounts.isClearing || accounts.statuses.values.contains(.checking))
                }
            } header: {
                Text("외부 AI 웹 계정")
            } footer: {
                Text("API 키 없이 각 서비스의 공식 웹 페이지에 직접 로그인한 세션을 사용합니다. 앱은 비밀번호를 읽거나 저장하지 않습니다. 상태는 계정 메뉴 같은 로그인 표식이 보일 때만 '로그인됨'으로 표시하며, 20초 안에 확인하지 못하면 '확인 안 됨'입니다.")
            }

            Section {
                Toggle("AI 브라우저 항상 보기", isOn: $accounts.alwaysShowBrowser)
            } header: {
                Text("표시 방식")
            } footer: {
                Text("끄면(기본값) 브라우저를 숨긴 채 진행하고 결과 창 상단에 단계·남은 시간·취소를 표시합니다. 로그인·보안 확인이 필요하거나 자동 입력이 맞지 않을 때만 브라우저 창을 띄웁니다. 켜면 처음부터 브라우저 창을 보여 줍니다.")
            }

            Section {
                ForEach(AIProvider.allCases) { provider in
                    HStack {
                        Text(provider.title).frame(width: 80, alignment: .leading)
                        Text(accounts.hasConsent(provider) ? "동의함" : "아직 동의하지 않음")
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button("동의 철회") { accounts.revokeConsent(provider) }
                            .disabled(!accounts.hasConsent(provider))
                    }
                }
            } header: {
                Text("메일 전송 동의")
            } footer: {
                Text("동의한 제공사는 메일 번역을 요청할 때 제목·본문 텍스트·이미지 글자(OCR)를 바로 보냅니다. 철회하면 다음 번역 실행 때 다시 묻습니다.")
            }

            Section {
                Button("외부 AI 로그인 세션 모두 지우기…", role: .destructive) { confirmClear = true }
                    .disabled(accounts.isClearing)
            } header: {
                Text("세션")
            } footer: {
                Text("이 앱의 외부 AI 브라우저에 저장된 쿠키·로그인 정보를 모두 지웁니다(Safari와 메일 서식 보기에는 영향 없음). 지운 뒤 각 제공사는 '로그인 필요'로 표시되며 '로그인…'으로 다시 로그인할 수 있습니다.")
            }

            Section {
                if let url = diagnostics.exportURL {
                    ShareLink(item: url) { Label("최근 외부 AI 진단 로그 공유…", systemImage: "square.and.arrow.up") }
                } else {
                    Text("아직 기록된 외부 AI 실행이 없습니다.").foregroundStyle(.secondary)
                }
                if let error = diagnostics.storageError {
                    Text(error).foregroundStyle(.orange)
                }
            } header: {
                Text("진단")
            } footer: {
                Text("최근 10회 실행의 단계 이름, 경과 시간, 개수만 이 Mac에 보관합니다. 메일 내용·요청문·답변·계정·쿠키·주소·오류 원문은 기록하지 않으며, 공유를 누를 때만 내보냅니다.")
            }
        }
        .formStyle(.grouped)
        .frame(width: 560)
        .frame(minHeight: 620)
        .aibiHiddenSurface()
        .confirmationDialog("외부 AI 로그인 세션을 모두 지울까요?", isPresented: $confirmClear) {
            Button("모두 지우기", role: .destructive) {
                Task { await accounts.clearAllSessions() }
            }
        } message: {
            Text("ChatGPT·Claude·Gemini 모두 다시 로그인해야 합니다. 진행 중인 외부 AI 번역은 취소됩니다.")
        }
        .onAppear { accounts.refreshAll() }
    }

    @ViewBuilder
    private func statusLabel(_ status: AIBILoginStatus?) -> some View {
        switch status {
        case .authenticated?:
            Label(AIBILoginStatus.authenticated.label, systemImage: "checkmark.circle.fill").foregroundStyle(.green)
        case .loginRequired?:
            Label(AIBILoginStatus.loginRequired.label, systemImage: "exclamationmark.circle.fill").foregroundStyle(.red)
        case .checking?:
            HStack(spacing: 4) { ProgressView().controlSize(.mini); Text(AIBILoginStatus.checking.label) }.foregroundStyle(.secondary)
        case .unknown?, nil:
            Label(AIBILoginStatus.unknown.label, systemImage: "questionmark.circle").foregroundStyle(.secondary)
        }
    }
}
