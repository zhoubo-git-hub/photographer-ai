@echo off
REM 启动本地 PostgreSQL（Docker 方式）
REM 前置：本机已安装 Docker Desktop 且已启动
cd /d "%~dp0.."
echo Starting PostgreSQL via Docker Compose ...
docker compose up -d
echo.
echo PostgreSQL 已启动：
echo   host    : localhost
echo   port    : 5432
echo   db      : photogai
echo   user/pass: postgres / postgres
echo.
echo 接着另开一个终端跑后端：scripts\start-backend.bat
pause
