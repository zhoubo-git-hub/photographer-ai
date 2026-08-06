@echo off
REM 启动后端（本机 mvn 方式）
REM 前置：JDK 17+ 与 Maven 已安装；PostgreSQL 已起（见 start-db.bat 或本机直装 PG）
cd /d "%~dp0.."
set DB_URL=jdbc:postgresql://localhost:5432/photogai
set DB_USERNAME=postgres
set DB_PASSWORD=postgres
set JWT_SECRET=change-me-to-a-long-random-secret-key-at-least-32-bytes-long!!
echo Starting photographer-ai-backend on http://localhost:8080 ...
call mvn spring-boot:run
pause
