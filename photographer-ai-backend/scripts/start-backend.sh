#!/usr/bin/env bash
# 启动后端（本机 mvn 方式）
# 前置：JDK 17+ 与 Maven 已安装；PostgreSQL 已起（见 start-db.sh 或本机直装 PG）
set -e
cd "$(dirname "$0")/.."
export DB_URL=jdbc:postgresql://localhost:5432/photogai
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=change-me-to-a-long-random-secret-key-at-least-32-bytes-long!!
echo "Starting photographer-ai-backend on http://localhost:8080 ..."
mvn spring-boot:run
