@echo off
echo Starting k6 performance test with Dashboard + InfluxDB...
echo Dashboard: http://localhost:5665
echo InfluxDB: http://localhost:8086
echo Grafana: http://localhost:3000 (admin/admin)
echo.

REM Run k6 test with dashboard and InfluxDB (InfluxDB 1.8 Alpine - write auth disabled)
C:\temp\xk6-build\k6.exe run --out dashboard --out influxdb=http://127.0.0.1:8086/k6 ./performance-test/concert-reservation-test.js

echo.
echo Test completed! Check metrics in:
echo - Dashboard: http://localhost:5665
echo - InfluxDB: http://localhost:8086
echo - Grafana: http://localhost:3000
pause