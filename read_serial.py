import serial
import time

try:
    ser = serial.Serial('COM12', 115200, timeout=1)
    ser.dtr = True
    ser.rts = True
    print("Opened COM12 successfully.")
    t_end = time.time() + 10
    while time.time() < t_end:
        line = ser.readline()
        if line:
            print(line.decode('utf-8', errors='replace').rstrip())
    ser.close()
except Exception as e:
    print("Error:", e)
