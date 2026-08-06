#!/usr/bin/env bash
# 启动本地 PostgreSQL（Docker 方式）
# 前置：本机已安装 Docker 且已启动
set -e
cd "$(dirname "$0")/.."
echo "Starting PostgreSQL via Docker Compose ..."
docker compose up -d
echo
echo "PostgreSQL 已启动："
echo "  host    : localhost"
echo "  port    : 5432"
echo "  db      : photogai"
echo "  user/pass: postgres / postgres"
echo
echo "接着跑后端： scripts/start-backend.sh"
