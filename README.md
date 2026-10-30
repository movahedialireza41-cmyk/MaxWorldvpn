<div align="center">

# MaxWorld

**کلاینت V2Ray / Xray برای اندروید، سریع، زیبا و کامل**
**A fast, beautiful and complete V2Ray / Xray client for Android**

![Platform](https://img.shields.io/badge/platform-Android%208%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Core](https://img.shields.io/badge/core-Xray-1f6feb)
![Languages](https://img.shields.io/badge/languages-فارسی%20%7C%20English-orange)

[دانلود آخرین نسخه · Download latest](../../releases/latest)

</div>

---

## فارسی

### MaxWorld چیست؟
MaxWorld یک برنامه VPN برای اندروید است که با هسته **Xray** کار می‌کند. هدفش این است که اتصال، مدیریت سرورها و اشتراک‌ها ساده باشد و ظاهرش هم از برنامه‌های هم‌رده بهتر باشد.

### امکانات

**اتصال و پروتکل‌ها**
- VLESS، VMess، Trojan، Shadowsocks، WireGuard و Hysteria2
- Reality، TLS، WebSocket، gRPC، HTTPUpgrade، XHTTP، KCP
- سرور زنجیره‌ای (Chain) و توزیع بار (Balance)
- Fragment، Mux و انتخاب اثر انگشت TLS
- کانفیگ JSON دلخواه Xray

**مدیریت سرور و اشتراک**
- افزودن با لینک، کلیپ‌بورد، QR، فایل پشتیبان یا لینک `v2rayng://`
- اشتراک‌ها با به‌روزرسانی خودکار (۶، ۱۲، ۲۴ یا ۴۸ ساعت)
- تست پینگ و تست سرعت هر سرور، مرتب‌سازی و جستجو
- انتخاب خودکار بهترین سرور و **جابه‌جایی خودکار** وقتی سروری از کار می‌افتد

**حجم و انقضا**
- نمایشگر حجم باقی‌مانده و روزهای مانده روی صفحه اصلی، مستقیم از اطلاعات ارائه‌دهنده
- مصرف داده هر سرور
- هشدار وقتی حجم رو به اتمام است یا اشتراک نزدیک انقضاست

**سپر (مسدودسازی تبلیغ و ردیاب)**
- لیست‌های آماده (StevenBlack، AdGuard، Peter Lowe) یا لینک لیست دلخواه
- لیست مجاز برای سایت‌هایی که نباید مسدود شوند
- روی همه برنامه‌های گوشی کار می‌کند، نه فقط مرورگر

> تبلیغات داخل ویدیوی یوتیوب با هیچ VPN ای مسدود نمی‌شود. برای آن در مرورگر از uBlock Origin استفاده کن.

**مسیریابی و حریم خصوصی**
- برنامه‌هایی که بدون VPN کار کنند (Split Tunneling)
- سایت‌ها و IP های ایرانی بدون VPN
- DNS دلخواه و DNS روی HTTPS
- **کل‌سوییچ** (Kill Switch): اگر VPN قطع شد، اینترنت هم قطع می‌شود تا IP لو نرود
- قفل برنامه با PIN و اثر انگشت

**ابزارها**
- اسکنر IP تمیز برای سرورهای پشت کلودفلر
- عیب‌یاب خودکار (Doctor) و گزارش لاگ
- پشتیبان‌گیری و بازیابی
- نمایش آی‌پی و موقعیت خروجی روی کره زمین
- ویجت، کاشی تنظیمات سریع و اتصال خودکار

**ظاهر**
- چندین تم، تم شخصی‌سازی‌شده، انیمیشن‌های روان
- فارسی (راست‌به‌چپ) و انگلیسی
- نمایش سرعت زنده در اعلان

**به‌روزرسانی داخل برنامه**
- وقتی نسخه جدید منتشر شود، یک کارت در تنظیمات می‌آید
- دانلود با درصد پیشرفت و نصب مستقیم، بدون رفتن به مرورگر

### نصب
1. به صفحه [Releases](../../releases/latest) برو.
2. فایل `MaxWorld-x.y.z.apk` را دانلود کن.
3. فایل را باز و نصب کن. اگر اندروید پرسید، اجازه «نصب از این منبع» را بده.

> اگر هنگام نصب خطای «package conflicts» گرفتی، نسخه قبلی با کلید امضای دیگری ساخته شده. از تنظیمات برنامه‌ها آن را یک بار پاک کن (قبلش پشتیبان بگیر) و دوباره نصب کن. از آن به بعد آپدیت‌ها روی هم نصب می‌شوند.

### ساخت از روی سورس
بیلد کامل (هسته Xray، تونل و APK) روی **GitHub Actions** انجام می‌شود و نیازی به نصب چیزی روی کامپیوتر نیست:

1. کد را در یک ریپو بگذار و فایل ورک‌فلو (`BUILD_WORKFLOW.yml`) را در مسیر `.github/workflows/build.yml` کپی کن.
2. در **Settings ← Actions ← General ← Workflow permissions** گزینه **Read and write permissions** را انتخاب کن.
3. در تب **Actions** ورک‌فلو را اجرا کن.
4. بعد از اتمام، APK به‌صورت خودکار در بخش **Releases** با تگ `v<نسخه>` منتشر می‌شود.

برای انتشار نسخه جدید فقط `versionCode` و `versionName` را در `app/build.gradle.kts` بالا ببر و بیلد بگیر. برنامه‌های نصب‌شده خودشان خبر می‌دهند.

### ساختار پروژه
```
app/src/main/java/com/maxworld/app/
  MaxVpnService.kt   سرویس VPN و کنترل اتصال
  XrayConfig.kt      ساخت کانفیگ Xray از سرور و تنظیمات
  LinkParser.kt      خواندن لینک‌های اشتراک‌گذاری
  Subs.kt            اشتراک‌ها و اطلاعات حجم
  Shield.kt          مسدودسازی تبلیغ و ردیاب
  Updater.kt         بررسی و دانلود آپدیت از GitHub
  *Screen.kt         صفحه‌ها (Compose)
```

### سپاس
- [Xray-core](https://github.com/XTLS/Xray-core) و [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [Iran-v2ray-rules](https://github.com/Chocolate4U/Iran-v2ray-rules)
- لیست‌های [StevenBlack](https://github.com/StevenBlack/hosts) و [AdGuard](https://github.com/AdguardTeam/AdGuardSDNSFilter)

---

## English

### What is MaxWorld?
MaxWorld is an Android VPN client built on the **Xray** core. It aims to make connecting and managing servers and subscriptions simple, with a design that stands out.

### Features
- **Protocols:** VLESS, VMess, Trojan, Shadowsocks, WireGuard, Hysteria2, with Reality / TLS and WebSocket, gRPC, HTTPUpgrade, XHTTP and KCP transports; chain and load-balance groups; custom Xray JSON
- **Servers and subscriptions:** import from link, clipboard, QR, backup or `v2rayng://`; scheduled subscription refresh; ping and speed tests; automatic best-server choice and automatic failover
- **Data and expiry:** remaining data and days on the home screen straight from the provider, per-server usage, alerts when data is nearly gone or a subscription is about to expire
- **Shield:** ad and tracker blocking with ready-made or your own lists and an allow list, working across all apps (ads inside YouTube videos cannot be blocked by any VPN)
- **Routing and privacy:** split tunneling, Iran-direct routing, custom DNS and DNS over HTTPS, kill switch, app lock with PIN and fingerprint
- **Tools:** clean-IP scanner for Cloudflare-fronted servers, built-in troubleshooter, logs, backup and restore, exit-IP globe, widget and quick-settings tile
- **Look:** several themes plus a custom one, smooth animations, Persian (RTL) and English
- **In-app updates:** a card appears when a new release exists; download with a progress percentage and install without opening a browser

### Install
Download `MaxWorld-x.y.z.apk` from the [latest release](../../releases/latest) and install it. Allow "install from this source" if Android asks.

### Build
The whole build (Xray core, tunnel library and APK) runs on GitHub Actions. Copy `BUILD_WORKFLOW.yml` to `.github/workflows/build.yml`, give workflows read and write permission (Settings → Actions → General), and run it. The APK is published to Releases as `v<versionName>` automatically.

### Credits
Xray-core, AndroidLibXrayLite, hev-socks5-tunnel, Iran-v2ray-rules, StevenBlack hosts and AdGuard DNS filter.
