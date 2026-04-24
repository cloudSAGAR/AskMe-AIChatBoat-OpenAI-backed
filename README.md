# AskMeChat

-----------------------
-----------
Created by:  SAGAR RAVAL 
 ||  email: saagar.raval@gmail.com  ||  +91 9824969150

<img width="252" height="81" alt="CS water mark" src="https://github.com/user-attachments/assets/f4f531f0-5b9a-497a-a4d1-a819c03bf734" />

A streaming AI chatbot Android app built as a clean, demo-ready reference
implementation. The UI and streaming behaviour are wrapped around a strict
Clean Architecture boundary so the app can be extended to any AI provider
by changing a single file.

<img width="300" height="625" alt="Splash" src="https://github.com/user-attachments/assets/8ede473e-ca35-45ed-8a37-7968480290c8" />
<img width="300" height="625" alt="Home" src="https://github.com/user-attachments/assets/1360de9b-da59-4fa6-91b9-de5fa6d9e939" />
<img width="300" height="625" alt="Chat" src="https://github.com/user-attachments/assets/a8243552-1928-4692-9920-74fd8f02a096" />


-----------

## 🚀 Quick Start — get an API key, paste, run

The app ships with three swappable providers. Default is **Groq** because it's
the fastest path to a working demo — truly free, no credit card, works
everywhere. Follow the three-minute setup for your preferred provider, then
rebuild.

### Option A — Groq (recommended, default) ⭐

1. Open **https://console.groq.com/keys**
2. Sign in with **Google, GitHub or email** — no card needed.
3. Click **Create API Key** → give it a name → **copy the key** (starts with `gsk_…`).
4. Paste it into `app/src/main/java/com/example/askmechat/data/remote/groq/GroqConfig.kt`:

   ```kotlin
   const val API_KEY: String = "gsk_…your key here…"
   const val MODEL_NAME: String = "llama-3.3-70b-versatile"   // or llama-3.1-8b-instant, gemma2-9b-it
   ```

5. Rebuild and run. The first prompt streams from Llama-3.3-70B.

**Why Groq:** Free tier is actually free (30 req/min on Llama-3.3-70B), extremely
fast inference (LPU hardware), no regional restrictions, no `limit: 0` surprises.

### Option B — Google Gemini

Gemini's free tier is now region-dependent — many new accounts show
`Billing Tier: Free tier` in the AI Studio console but actual per-model
quotas of `0`. If that happens to you, you'll need to click **"Set up
billing"** on the API Keys page (you still pay nothing under the free
usage threshold).

1. Open **https://aistudio.google.com/app/apikey**
2. Click **Create API Key → Create API key in new project**.
3. Paste it into `GeminiConfig.kt`:

   ```kotlin
   const val API_KEY: String = "AIzaSy…your key here…"
   const val MODEL_NAME: String = "gemini-2.0-flash"   // or gemini-flash-latest, gemini-2.5-flash
   ```

4. Open `ChatServiceLocator.kt` and switch the default:

   ```kotlin
   var activeProvider: Provider = Provider.GEMINI
   ```

5. If the first request returns `HTTP 429 / limit: 0`, click **"Set up
   billing"** next to your key in AI Studio and rebuild.

**Valid model names on v1beta** (the old `gemini-1.5-flash` is retired and
returns `404`):

- `gemini-2.0-flash`  — recommended free-tier flagship
- `gemini-flash-latest`  — rolling alias
- `gemini-2.5-flash`  — newer, slightly tighter quota
- `gemini-pro-latest`  — higher quality, smaller free tier

### Option C — your own streaming REST backend

1. Open `ChatEndpoints.kt`:

   ```kotlin
   const val CHAT_BASE_URL: String = "https://api.mybackend.com/"
   const val CHAT_PATH: String = "v1/chat"
   ```

2. Switch the active provider:

   ```kotlin
   var activeProvider: Provider = Provider.CUSTOM_REST
   ```

3. Your backend must return line-delimited JSON with `type: "begin" | "item" | "end"`
   chunks — see the *Custom REST* section further down for the exact shape.

### Switching providers later

All three impls live behind the same `domain/repository/ChatRepository`
interface, so switching is a one-line change in
`di/ChatServiceLocator.kt`:

```kotlin
var activeProvider: Provider = Provider.GROQ     // ⭐ default — free, no card
// or
var activeProvider: Provider = Provider.GEMINI   // Google Gemini (may need billing)
// or
var activeProvider: Provider = Provider.CUSTOM_REST  // your own backend
```

The ViewModel, adapters, XML layouts and the entire domain layer are
untouched — that's the Clean Architecture payoff.

-----------

https://github.com/user-attachments/assets/d9740295-ab40-478e-88fa-49863cf76ac3

-----------

## Highlights

- Real streaming chat — AI response appears character-by-character as it
  arrives over the wire, not after a long wait.
- Breathing-dot typing cursor (ChatGPT-style) while text streams.
- Six-dot wave loading animation while waiting for the first chunk.
- Suggestion chips above the input with a one-shot shimmer warm-up.
- Markdown-lite rendering in AI bubbles — **bold**, numbered lists, bullet
  lists, `##` / `###` headers.
- Map-points carousel — when the backend attaches a visualization block,
  the chat bubble becomes tappable and shows a horizontal list of places.
- Keyboard-aware layout that slides the input panel up without reflowing
  the chat list; keyboard-first back-press dismissal.
- Auto-retry on resume if the stream was aborted while the app was
  backgrounded.
- Wake- and Wi-Fi-locks during streaming so long responses survive brief
  backgrounding (graceful no-op if permission is denied).
- Single-file endpoint configuration — swap providers in seconds.

---

## Tech Stack

| Layer            | Technology                                           |
|------------------|------------------------------------------------------|
| Language         | Kotlin 2.2.0                                         |
| Build            | Gradle 8.13, AGP 8.13.2, Kotlin DSL                  |
| Min / Target SDK | 24 / 36                                              |
| UI               | View system (Fragment + ViewBinding + DataBinding)   |
| Async            | Coroutines + Flow                                    |
| Networking       | Retrofit 2 + OkHttp 4 + Gson                         |
| DI               | Manual service locator (Hilt-ready)                  |
| Persistence      | Room 2.6 (scaffolded; not yet used)                  |
| Misc             | Parcelize, Material 3, ConstraintLayout, RecyclerView |

---

## Architecture

Strict Clean Architecture with one-way dependencies:

```
┌─────────────────┐          ┌──────────────┐
│  presentation   │   ──▶    │    domain    │    ◀──   │ data │
│  (Fragment,     │          │  (models,    │         │ (Retrofit, │
│   ViewModel,    │          │   use cases, │         │  DTOs,     │
│   adapters,     │          │   repository │         │  impls)    │
│   widgets)      │          │   interface) │         │           │
└─────────────────┘          └──────────────┘          └─────────┘
          └──────────── di (service locator) ──────────┘
```

- `domain` is pure Kotlin — no Android, no Retrofit, no Gson.
- `data` implements the domain repository contract; swappable per provider.
- `presentation` consumes use cases (not repositories directly) and renders
  a **single `StateFlow<ChatScreenState>`** for atomic UI updates.

### Streaming contract

The repository emits a `Flow<StreamChunk>` with these phases:

```
Loading  →  Streaming*  →  (MapData)?  →  Success | PartialSuccess | Error
```

Making streaming a first-class domain concept means non-streaming providers
simply never emit `Streaming` chunks — the ViewModel doesn't care.

---

## Project Structure

```
AskMeChat/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/askmechat/
│   │   │   ├── MainActivity.kt
│   │   │   │
│   │   │   ├── di/
│   │   │   │   └── ChatServiceLocator.kt
│   │   │   │
│   │   │   ├── domain/                          Pure Kotlin (no Android)
│   │   │   │   ├── model/
│   │   │   │   │   ├── ChatMessage.kt           User / AI messages
│   │   │   │   │   ├── MapPoint.kt
│   │   │   │   │   ├── MapPointGroup.kt
│   │   │   │   │   └── StreamChunk.kt           Streaming contract
│   │   │   │   ├── repository/
│   │   │   │   │   └── ChatRepository.kt        INTERFACE
│   │   │   │   └── usecase/
│   │   │   │       ├── SendMessageUseCase.kt
│   │   │   │       └── GetStarterSuggestionsUseCase.kt
│   │   │   │
│   │   │   ├── data/
│   │   │   │   ├── remote/
│   │   │   │   │   ├── api/                      Custom REST backend
│   │   │   │   │   │   ├── ChatApiService.kt    Retrofit interface
│   │   │   │   │   │   └── ChatEndpoints.kt     ⚠ URL config
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── ChatRequestDto.kt
│   │   │   │   │   │   ├── ChatStreamResponseDto.kt
│   │   │   │   │   │   └── VisualizationResponseDto.kt
│   │   │   │   │   ├── mapper/
│   │   │   │   │   │   └── ChatStreamMapper.kt  DTO → domain
│   │   │   │   │   ├── gemini/                   Google Gemini backend
│   │   │   │   │   │   ├── GeminiConfig.kt      ⚠ API key + model
│   │   │   │   │   │   └── GeminiDtos.kt        Wire DTOs
│   │   │   │   │   ├── groq/                     Groq (default)
│   │   │   │   │   │   ├── GroqConfig.kt        ⚠ API key + model
│   │   │   │   │   │   └── GroqDtos.kt          OpenAI-compat DTOs
│   │   │   │   │   └── network/
│   │   │   │   │       └── RetrofitProvider.kt  Shared OkHttp client
│   │   │   │   └── repository/                   One impl per provider
│   │   │   │       ├── ChatRepositoryImpl.kt    Custom REST
│   │   │   │       ├── GeminiChatRepositoryImpl.kt
│   │   │   │       └── GroqChatRepositoryImpl.kt
│   │   │   │
│   │   │   └── presentation/
│   │   │       └── chat/
│   │   │           ├── ChatBoatFragment.kt
│   │   │           ├── ChatViewModel.kt
│   │   │           ├── ChatViewModelFactory.kt
│   │   │           ├── state/
│   │   │           │   └── ChatScreenState.kt   Single UI state
│   │   │           ├── adapter/
│   │   │           │   ├── ChatAdapter.kt       Streaming, typing, bold
│   │   │           │   ├── ChatDiffUtil.kt
│   │   │           │   ├── SuggestionChipAdapter.kt
│   │   │           │   └── MapPointsAdapter.kt
│   │   │           └── widget/
│   │   │               ├── DotsLoadingView.kt
│   │   │               ├── TypingCursorSpan.kt
│   │   │               └── SuggestionChipShimmerView.kt
│   │   │
│   │   ├── res/
│   │   │   ├── layout/                          Chat screen + items
│   │   │   ├── drawable/                        Bubble / input / map tiles
│   │   │   └── values/                          Colors, strings, theme
│   │   │
│   │   └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── gradle/
│   └── libs.versions.toml                       Version catalog
│
├── build.gradle.kts
├── settings.gradle.kts
└── README.md                                    ← you are here
```

---

## Getting Started

### Prerequisites

- **Android Studio** Koala (2024.1) or newer — Iguana and older will complain
  about Kotlin 2.2.0.
- **JDK 17** on the toolchain path (Android Studio bundles it).
- **Android SDK** with API 36 platform installed.
- An emulator or physical device on API 24+.

### Clone & Open

```bash
git clone <your-remote>/AskMeChat.git
cd AskMeChat
```

Open the root folder in Android Studio → Gradle sync will download all
dependencies (Retrofit, OkHttp, Material, etc.).

### AI provider details

The quick-start at the top of this README covers the fastest path for each
provider. Below is the full technical breakdown of what each
implementation does on the wire.

#### Groq (default)  — `GroqChatRepositoryImpl.kt`

- **Endpoint:** `POST https://api.groq.com/openai/v1/chat/completions`
- **Auth:** `Authorization: Bearer <API_KEY>` header.
- **Protocol:** OpenAI-compatible Chat Completions with `stream: true`.
- **Response:** SSE lines `data: {json} … data: [DONE]`; each chunk has
  `choices[0].delta.content`.
- **Multi-turn:** per-session `MutableList<GroqMessageDto>` — the system
  prompt is the first entry, user turns and assistant replies are
  appended as `role: "user"` and `role: "assistant"` respectively.
- **Config:** `data/remote/groq/GroqConfig.kt` — API key, model name,
  system instruction, temperature, max tokens.

Default model is `llama-3.3-70b-versatile`. Swap to
`llama-3.1-8b-instant` for faster/cheaper responses or `gemma2-9b-it`
for Google's open model.

#### Gemini  — `GeminiChatRepositoryImpl.kt`

- **Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse&key={API_KEY}`
- **Protocol:** Gemini's native REST streaming — no SDK, no Ktor (we
  abandoned the official `com.google.ai.client.generativeai` SDK because
  its Ktor transitive deps fail with
  `Failed resolution of: Lio/ktor/client/plugins/HttpTimeout` on
  device).
- **Response:** SSE lines `data: {json}`; each chunk has
  `candidates[0].content.parts[0].text`.
- **Multi-turn:** per-session `MutableList<GeminiContentDto>` — turns
  are appended with `role: "user"` / `role: "model"`.
- **Error mapping:** `friendlyErrorFor(httpCode, body)` in the repo
  translates 401/403/404/429/5xx plus the `limit: 0` free-tier-zeroed
  case into short, actionable messages instead of dumping raw JSON.
- **Config:** `data/remote/gemini/GeminiConfig.kt`.

#### Custom REST  — `ChatRepositoryImpl.kt`

Your own backend. Expected wire format (line-delimited JSON):

```json
{"type":"begin","metadata":{...}}
{"type":"item","content":"Hello "}
{"type":"item","content":"there!"}
{"type":"end"}
```

`type` takes one of: `begin`, `item`, `end`. Optional visualization
blocks are wrapped in the same envelope but contain a JSON string whose
body has `visualizationType` and `mapData`. See `ChatRepositoryImpl` for
the exact parsing rules.

Config: `data/remote/api/ChatEndpoints.kt` — base URL, path, optional
suggestions endpoint.

#### Adding a fourth provider (OpenAI, Claude, Mistral, …)

1. Create `data/repository/XyzChatRepositoryImpl.kt` implementing
   `domain.repository.ChatRepository`.
2. Emit `StreamChunk`s on the returned flow.
3. Add a new `Provider` enum entry in `ChatServiceLocator.kt` and a
   `when` branch constructing your impl.

No presentation-layer or domain-layer code changes.

### Build & Run

From the IDE: press **Run ▶** with an emulator running.

From the CLI:

```bash
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:installDebug           # install on a running device
./gradlew clean                       # nuke build artifacts
```

---

## Using the app

1. Launch AskMeChat. You'll see two starter suggestion chips above the input.
2. Tap a chip to pre-fill the input, or type your own question.
3. Hit the circular Send button.
4. The dots loader appears in an AI bubble while the first chunk is awaited.
5. When text starts arriving, it's typed into the bubble character by
   character, with a breathing-dot cursor at the end.
6. If the backend sends a visualization block, a horizontal strip of map
   points slides up above the input; tap one to open the map view
   (currently a Toast placeholder — see *Extending* below).
7. Tap an older AI bubble that has map data to re-display its points.

Back-press behaviour: if the keyboard is open, back dismisses the keyboard
(Samsung 3-button nav is specifically handled). A second back closes the
screen.

---

## Extending

### Swap the AI provider

Everything that talks to the backend is behind `ChatRepository` in
`domain/repository/`. To plug in another provider:

1. Create a new repository impl, e.g. `GeminiChatRepositoryImpl` under
   `data/repository/`.
2. Emit `StreamChunk`s on the flow.
3. Point `ChatServiceLocator.chatRepository` at the new impl.

Neither the ViewModel nor the Fragment nor any domain code changes.

### Add offline history (Room)

1. Add `data/local/dao/MessageDao.kt` and `data/local/entity/MessageEntity.kt`.
2. Add a new `ConversationsRepository` interface under
   `domain/repository/` and an impl that combines Room + the remote repo.
3. Wire it through `ChatServiceLocator`.

### Add tool-use / function-calling

1. Extend `StreamChunk` with `ToolCallRequested(val toolName: String, val args: Map<String, Any>)`.
2. Add `HandleToolCallUseCase` in `domain/usecase/`.
3. In the ViewModel reducer, handle the new chunk by invoking the use case
   and appending a "tool result" AI message.

### Replace the Toast map-view with a real screen

In `ChatBoatFragment.openMapView()`, replace the Toast with a
`FragmentTransaction` to a new `MapViewFragment`. Pass `List<MapPoint>`
via a Parcelable wrapper (the domain model is deliberately
Parcelable-free; wrap it in a Parcelize class in the presentation layer).

### Migrate to Hilt

1. Delete `di/ChatServiceLocator.kt`.
2. Add `@HiltAndroidApp` to a new `AskMeApp` class.
3. Annotate `ChatRepositoryImpl` with `@Inject constructor(...)`.
4. Annotate `ChatViewModel` with `@HiltViewModel`.
5. Delete `ChatViewModelFactory` and use `by viewModels()` plain.

### Migrate to Jetpack Compose

- The `StateFlow<ChatScreenState>` pattern is already Compose-friendly —
  collect as state in a `@Composable` and the whole View layer (Fragment,
  adapters, widgets, XML) becomes optional.
- Keep `domain` and `data` untouched.

---

## Permissions

Declared in `AndroidManifest.xml`:

- `INTERNET` — required.
- `ACCESS_NETWORK_STATE` — required.
- `WAKE_LOCK` — used by streaming locks; harmless if denied.

---

## Troubleshooting

**"Module was compiled with an incompatible version of Kotlin"**
→ The project is pinned to Kotlin 2.2.0 / KSP 2.2.0-2.0.2 to match the
`databinding-ktx` shipped with AGP 8.13.2. Don't downgrade Kotlin without
also downgrading AGP.

**Gemini: `HTTP 429 / limit: 0` on a brand-new key**
→ Your Google account has no free-tier quota for that model. Either
(a) click **"Set up billing"** on the API Keys page — still pay-as-you-go
with a free monthly credit, (b) create a new key in a fresh Cloud project
with the Generative Language API enabled, or (c) switch the provider to
`GROQ` in `ChatServiceLocator.kt` for a truly free alternative.

**Gemini: `HTTP 404 — model not found for API version v1beta`**
→ The old `gemini-1.5-flash` aliases were retired. Update
`GeminiConfig.MODEL_NAME` to `gemini-2.0-flash`, `gemini-flash-latest`,
`gemini-2.5-flash` or `gemini-pro-latest`.

**Groq: `HTTP 401 Unauthorized`**
→ The key is wrong or unknown. Confirm the value on
https://console.groq.com/keys — Groq keys start with `gsk_`.

**Stream chunks arrive all at once instead of streaming**
→ Check `RetrofitProvider.kt` — the OkHttp logging interceptor MUST stay
at `Level.HEADERS`. `Level.BODY` buffers the entire response before
forwarding it, which defeats `@Streaming`. Error responses are still
fully logged via the repo's `logLongLines` helper — they don't need
interceptor-level body logging.

**"Method setDotYn() with type float not found on target class DotsLoadingView"**
→ Fixed in the current code — the dots-loading animation was switched
from `ObjectAnimator.ofFloat(this, "dotY$i", …)` to `ValueAnimator.ofFloat(…)`
because the values are assigned directly from `addUpdateListener`.

**`ChatEndpoints` still points at `example.com` and the app crashes**
→ Either edit the endpoints file or, simpler, switch to the Groq or
Gemini provider in `ChatServiceLocator.kt`.

**Keyboard doesn't push the input up on Samsung devices**
→ The fragment uses a WindowInsets listener plus a height-based fallback
plus a timestamp grace window specifically for Samsung's early-dismiss
race. Verify that `android:windowSoftInputMode="adjustNothing"` is still
on the Activity in `AndroidManifest.xml`.

**How do I see the raw response body in Logcat?**
→ Filter by `GroqChatRepoImpl` or `GeminiChatRepoImpl`. Each error
response is dumped in chunks (Logcat drops > 4 000-char lines) so the
full JSON survives. Successful streams only log headers to preserve
the streaming behaviour.

# AskMe-AIChatBoat-OpenAI-backed

-----------------------
-----------
Created by: SAGAR RAVAL 
email: saagar.raval@gmail.com
+91 9824969160

<img width="2062" height="588" alt="CS water mark" src="https://github.com/user-attachments/assets/d350f217-38b8-41b7-a33d-6dff4e2a3ebf" />

