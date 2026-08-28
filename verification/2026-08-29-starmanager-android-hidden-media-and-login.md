# StarManager Android hidden media and login verification

- Date: 2026-08-29 KST
- Device under live verification: Android ADB target `192.168.0.142:5555`
- Additional installation targets: `192.168.0.166:5555`, `192.168.0.152:5555`
- Privacy: no prompt, account identity, cookie, token, media payload, or full generated answer was recorded.

## Hidden five-photo runs

- Three repeated main-phone runs returned `MODE_OPEN_MULTIPLE` with `prepared=5` and semantic preview `observed=5 expected=5`.
- Repeated accessibility samples reported zero `Gemini에서 만들기` and zero attachment-fallback banners while Browser View was off.
- The app process remained alive and each result returned to the StarManager host surface.
- A final post-cleaner run showed only the generated caption; `def check_len`, `print`, `draft`, and length-statistic scaffolding were absent.

### ChatGPT mobile WebView regression

- The current ChatGPT mobile composer exposes a nested `+` menu with `카메라`, `사진`, and `파일` actions. A sanitized fixture is stored at `fixtures/providers/chatgpt-android-mobile-photo-menu.json`.
- The hidden WebView remained behind the opaque StarManager surface. A trusted touch opened the plus control, the first 500 ms discovery attempt found the semantic `사진` action, and a second trusted touch invoked `MODE_OPEN_MULTIPLE`.
- Two consecutive five-photo runs recorded `prepared=5` and `observed=5 expected=5`, showed zero `ChatGPT에서 만들기` or attachment-fallback surfaces in repeated accessibility samples, kept the process alive, and automatically returned the result to StarManager.
- No temporary alpha promotion or visible browser frame is used.

### Claude cross-provider verification

- Claude accepted the same ordered five-photo batch through the atomic DataTransfer fallback and recorded `observed=5 expected=5 path=data-transfer` before prompt submission.
- Two Claude runs completed without a visible `Claude에서 만들기` or attachment-fallback surface, kept the process alive, and automatically returned the result to StarManager.

### Hidden keyboard regression

- Root cause: the visible browser path blurred the provider editor, cleared WebView focus, and hid the Android input method after prompt injection, while the hidden path retained focus acquired by trusted attachment touches and provider editor insertion.
- The hidden host now blurs the active DOM element, clears WebView and activity focus, and hides the input method using both WebView and host-window tokens after attachment, prompt injection, submission, and submission verification. A delayed second hide covers WebView's queued IME-show frame.
- Physical-device verification sampled `mInputShown=false` 30 consecutive times during a five-photo ChatGPT task while also confirming zero visible browser or fallback surface. Attachment verification remained `observed=5 expected=5`, the process stayed alive, and the result returned to StarManager with the keyboard still hidden.

## Login management

- The settings screen displayed `상태 다시 확인` and `모두 로그아웃`.
- Provider probes started concurrently and reached a bounded result within 12 seconds.
- Final live result: Gemini, ChatGPT, and Claude all reached `로그인됨` within the first four-second UI sample. ChatGPT required three bounded sidebar-discovery polls before its current account marker hydrated.
- A later user-authorized full logout exposed a state overwrite: Gemini and ChatGPT became bounded `UNKNOWN` while Claude showed `REQUIRES_LOGIN`.
- Root cause: the host set all three providers to `REQUIRES_LOGIN` after clearing shared WebView storage, then immediately launched an automatic probe that overwrote deterministic logout evidence with ambiguous public-page results.
- The host now keeps all three providers at `REQUIRES_LOGIN` after confirmed session deletion. A provider-level explicit-logout fact contains no account or session content, survives app restart, and is removed when the user deliberately opens that provider's login flow.
- Physical-device result after the fix: Gemini, ChatGPT, and Claude all showed `로그인 필요` immediately after full logout and still showed `로그인 필요` after force-stop, relaunch, and reopening Settings. The app process remained alive.
- The final debug APK was installed successfully on all three designated Android targets.
- Debug unit tests, debug APK assembly, and the 22-test portable AIBI suite passed.
