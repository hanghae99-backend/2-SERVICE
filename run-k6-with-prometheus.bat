@echo off
echo Starting k6 performance test with Dashboard + Prometheus...
echo Dashboard: http://localhost:5665
echo Prometheus: http://localhost:9090
echo Grafana: http://localhost:3000 (admin/admin)
echo.

REM Run k6 test with both dashboard and prometheus output
C:\temp\xk6-build\k6.exe run --out dashboard --out experimental-prometheus-rw=http://localhost:9090/api/v1/write ./performance-test/concert-reservation-test.js

echo.
echo Test completed! Check metrics in:
echo - Dashboard: http://localhost:5665
echo - Prometheus: http://localhost:9090
echo - Grafana: http://localhost:3000
pause