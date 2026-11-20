"""
Giám sát trạng thái trực tuyến (Heartbeat) của thiết bị ESP32 Smart Home
Tự động gửi cảnh báo khẩn cấp tới Telegram khi thiết bị bị ngoại tuyến (Offline / mất điện / mất mạng)
"""
import time
import json
import os
import urllib.request
import urllib.error

FIREBASE_URL = os.environ.get("FIREBASE_HEARTBEAT_URL", "https://do-an-2-f791a-default-rtdb.asia-southeast1.firebasedatabase.app/devices/esp32-voice/timestamp.json")
TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "9149182719:AAEnY7TiKUqeDTmRL__SBV-TG66_x3g9ENU")
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "5568777313")
CHECK_INTERVAL_SEC = 15      # Kiểm tra mỗi 15 giây
OFFLINE_THRESHOLD_SEC = 75   # Quá 75 giây không gửi heartbeat -> Coi là Offline

def send_telegram(text):
    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    payload = json.dumps({
        "chat_id": TELEGRAM_CHAT_ID,
        "text": text,
        "parse_mode": "Markdown"
    }).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return True
    except Exception as e:
        print(f"[Telegram Error] {e}")
        return False

def get_device_timestamp():
    try:
        with urllib.request.urlopen(FIREBASE_URL, timeout=8) as resp:
            data = resp.read().decode("utf-8")
            val = json.loads(data)
            return int(val) if val is not None else 0
    except Exception:
        return 0

def main():
    print("=" * 60)
    print("  ESP32 SMART HOME - TELEGRAM OFFLINE MONITOR")
    print(f"  Target Chat ID: {TELEGRAM_CHAT_ID}")
    print(f"  Offline Threshold: {OFFLINE_THRESHOLD_SEC}s")
    print("=" * 60)

    is_currently_offline = False

    while True:
        now = int(time.time())
        ts = get_device_timestamp()

        if ts > 0:
            diff = now - ts
            if diff > OFFLINE_THRESHOLD_SEC:
                if not is_currently_offline:
                    is_currently_offline = True
                    msg = (
                        "🔴 *CẢNH BÁO: THIẾT BỊ NGOẠI TUYẾN!*\n"
                        f"⚠️ Thiết bị ESP32 Smart Home đã ngắt kết nối (mất điện hoặc mất Wi-Fi) quá {diff} giây.\n"
                        "Vui lòng kiểm tra nguồn điện hoặc router mạng nhà bạn!"
                    )
                    print(f"[{time.strftime('%H:%M:%S')}] ALERT: Device is OFFLINE (diff={diff}s)")
                    send_telegram(msg)
            else:
                if is_currently_offline:
                    is_currently_offline = False
                    msg = (
                        "🟢 *THÔNG BÁO: THIẾT BỊ ĐÃ KẾT NỐI LẠI!*\n"
                        "Thiết bị ESP32 Smart Home đã phục hồi kết nối Internet và trực tuyến trở lại bình thường."
                    )
                    print(f"[{time.strftime('%H:%M:%S')}] RECOVER: Device is back ONLINE")
                    send_telegram(msg)

        time.sleep(CHECK_INTERVAL_SEC)

if __name__ == "__main__":
    main()
