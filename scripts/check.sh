#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# 本机环境：Maven 需 JDK 21（Homebrew 默认 JDK25 不兼容 Lombok 1.18.34）
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home}"

echo "==> 后端测试 (server)"
(cd server && mvn -q test)

echo "==> 前端构建 (admin-web)"
(cd admin-web && npm run build)

echo "==> 全部通过 ✔"
