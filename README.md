# 📡 Beam

*Beam any website into a native Android TV experience — powered by AI.*

Paste or share a URL from your phone. Beam analyzes the page, extracts the content, and turns it into a beautiful TV browse screen. Pick a video. It plays.

No scraper maintenance. No site-specific plugins. Just AI.

---

## ✨ Features

- 🔗 Share any streaming website URL from your phone browser directly to your TV
- 🤖 AI automatically extracts titles, thumbnails, and categories from any page
- 📺 Native Android TV UI — fully D-pad and remote friendly
- 🎬 Video playback via ExoPlayer (HLS, MP4, DASH)
- 💾 Save your favorite sites for quick access
- 📱 Phone companion app — scan, connect, and send URLs to your TV instantly
- 🔑 Bring your own free AI API key (Gemini, Groq, or local Ollama)
- 🔒 API key stored locally on device — never sent to any external server
- 🌐 Works with any website that has video content

---

## 📱 Phone Companion App

The Beam phone app makes everything easier:

- *Auto-discovers* your Beam TV on the local network — no IP address typing
- *Share from browser* — tap Share → Beam in any browser to send URLs to your TV instantly
- *Send API key* — configure your TV's AI key from your phone without typing on the remote

---

## 🚀 Getting Started

### Requirements
- Android TV device running Android 6.0 (API 23) or higher
- Android phone running Android 5.0 or higher (for companion app)
- Both devices on the same WiFi network
- A free AI API key (see below)

### Installation

1. Download the latest APK from [Releases](https://github.com/codebyoketch/Beam/releases)
2. Install beam-tv.apk on your Android TV
3. Install beam-phone.apk on your Android phone
4. Open Beam on your TV
5. Open Beam on your phone — it will auto-discover your TV
6. Use the phone app to send your API key to the TV

### Getting a Free API Key

| Provider | Link | Notes |
|---|---|---|
| *Google Gemini* | https://aistudio.google.com | Recommended — generous free tier |
| *Groq* | https://console.groq.com | Very fast, Llama 3 |
| *Ollama* | https://ollama.com | Fully local, no key needed |

---

## 🎬 How to Use

1. Open Beam on your TV
2. Open Beam on your phone
3. Phone auto-discovers the TV on your network
4. In your phone browser, find a streaming site
5. Tap *Share → Beam*
6. The site appears on your TV as a native browse screen
7. Navigate with your remote, pick a video, hit Play

---

## 🏗️ Architecture


Phone Browser
      ↓ Share
Phone Beam App
      ↓ WiFi (local network)
TV Beam App
      ↓
HtmlFetcher (OkHttp + Jsoup)
      ↓
PromptBuilder → AI Provider (Gemini / Groq / Ollama)
      ↓
ParsedPage (structured content)
      ↓
Leanback BrowseFragment (native TV UI)
      ↓ select video
StreamExtractor → finds .m3u8 / .mp4 URL
      ↓
ExoPlayer (fullscreen playback)


---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| TV UI | AndroidX Leanback |
| Video Player | ExoPlayer (Media3) |
| HTTP | OkHttp + Jsoup |
| AI | Pluggable — Gemini / Groq / Ollama |
| Device Discovery | Android NsdManager (mDNS) |
| Local Server | Custom HTTP server (port 8765) |
| Secure Storage | EncryptedSharedPreferences |
| Async | Kotlin Coroutines |

---

## 📁 Project Structure


Beam/
├── tv/                          # Android TV app
│   └── src/main/java/com/beam/
│       ├── ai/                  # AI provider abstraction
│       │   ├── AIProvider.kt    # Provider interface
│       │   ├── GeminiProvider.kt
│       │   ├── GroqProvider.kt
│       │   ├── OllamaProvider.kt
│       │   ├── PageAnalyzer.kt  # Core AI analysis logic
│       │   └── PromptBuilder.kt # HTML → AI prompt
│       ├── scraper/
│       │   ├── HtmlFetcher.kt   # Fetches raw HTML
│       │   └── StreamExtractor.kt # Finds video stream URLs
│       ├── server/
│       │   ├── BeamServer.kt    # Local HTTP server (port 8765)
│       │   └── BeamDiscovery.kt # mDNS network registration
│       ├── model/               # Data models
│       └── ui/                  # TV screens
│           ├── MainActivity.kt
│           ├── HomeFragment.kt
│           ├── BrowseFragment.kt
│           ├── DetailFragment.kt
│           ├── PlaybackFragment.kt
│           └── SettingsFragment.kt
│
└── phone/                       # Android phone companion app
    └── src/main/java/com/beam/phone/
        ├── MainActivity.kt      # Main UI — scan, send URLs, send keys
        └── BeamScanner.kt       # mDNS TV discovery


---

## ⚠️ Known Limitations

- *JavaScript-heavy sites* return limited content — a WebView fallback is planned
- *DRM-protected streams* (Netflix, Disney+) cannot be played
- *Some streaming sites* obfuscate their video players — stream extraction may fail
- Phone-to-TV communication requires both devices on the *same WiFi network*

---

## 🗺️ Roadmap

- [ ] WebView fallback for JavaScript-heavy sites (YouTube, etc.)
- [ ] QR code pairing between phone and TV
- [ ] Cloud clipboard relay for cross-network URL sharing
- [ ] Voice URL input
- [ ] Saved sites with quick launch from home screen
- [ ] Auto-detect and update AI model names
- [ ] Subtitle support
- [ ] Picture-in-picture mode

---

## 🤝 Contributing

Pull requests are welcome! Areas that need help:

- Stream extraction improvements for specific sites
- WebView fallback implementation
- UI polish and animations
- Additional AI provider integrations
- Testing on more Android TV devices

Please open an issue first to discuss major changes.

---

## 📄 License

GPL-3.0 — see [LICENSE](LICENSE)

---

## ⚖️ Disclaimer

Beam is a tool for navigating websites on your TV. Users are responsible for ensuring they have the right to access any content they stream. Beam does not host, store, or distribute any media content.
