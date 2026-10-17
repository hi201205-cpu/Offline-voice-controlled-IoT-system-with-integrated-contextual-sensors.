import serial
import time
import sys

try:
    s = serial.Serial('COM12', 115200, timeout=1)
    print("Opened COM12 successfully", flush=True)
    t0 = time.time()
    while time.time() - t0 < 15:
        line = s.readline().decode('utf-8', errors='ignore').strip()
        if line:
            print(f"[{time.strftime('%H:%M:%S')}] {line}", flush=True)
    s.close()
except Exception as e:
    print(f"Error: {e}", flush=True)
