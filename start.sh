#!/usr/bin/env bash
# ============================================================================
# wordpilot 启动脚本（第 8 节写作编排后，写作全链路可运行）
#
# 职责：
#   1. 载入 .env（DB_PASSWORD / DASHSCOPE_API_KEY / BGE_MODEL_PATH，不入版本库）
#   2. 校验必需环境变量与中间件端口（MySQL 3309 / Redis 6379 / ES 9200）
#   3. 全模块构建（跳过测试，等价产物已被 mvn clean verify 验证）
#   4. 启动 writing-start fat jar（8080，SSE 长连接）
#
# 用法：
#   ./start.sh            # 前台启动，Ctrl+C 停止
#   ./start.sh -b         # 仅构建不启动（供调试）
#   ./start.sh -l         # 检查中间件与凭证就绪性（不启动）
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")"

BUILD_ONLY=false
CHECK_ONLY=false
for arg in "$@"; do
  case "$arg" in
    -b) BUILD_ONLY=true ;;
    -l) CHECK_ONLY=true ;;
    *) echo "未知参数: ${arg}（支持 -b 仅构建 / -l 仅检查）" >&2; exit 2 ;;
  esac
done

# ---------- 1. 载入 .env ----------
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
else
  echo "[start] 未找到 .env，将仅使用已导出的环境变量（凭证缺失时会报错）" >&2
fi

# ---------- 2. 校验凭证与中间件 ----------
: "${DB_PASSWORD:?缺少 DB_PASSWORD（在 .env 或环境变量中设置）}"
: "${DASHSCOPE_API_KEY:?缺少 DASHSCOPE_API_KEY（在 .env 或环境变量中设置）}"
: "${BGE_MODEL_PATH:?缺少 BGE_MODEL_PATH（在 .env 或环境变量中设置）}"
[ -d "$BGE_MODEL_PATH" ] || { echo "[start] BGE 模型目录不存在: $BGE_MODEL_PATH" >&2; exit 1; }
echo "[start] 凭证就绪: DB_PASSWORD=*** DASHSCOPE_API_KEY=sk-${DASHSCOPE_API_KEY:8}*** BGE=$BGE_MODEL_PATH"

for spec in "3309 MySQL" "6379 Redis" "9200 Elasticsearch"; do
  port="${spec%% *}"; name="${spec#* }"
  if nc -z -w 2 127.0.0.1 "$port" 2>/dev/null; then
    echo "[start] 中间件 OK: $name($port)"
  else
    echo "[start] 中间件不可达: $name($port) —— 请先启动 $name" >&2
    exit 1
  fi
done
[ "$CHECK_ONLY" = true ] && { echo "[start] 检查通过，未启动应用"; exit 0; }

# ---------- 3. 轻量构建（静态门禁 P3C/SpotBugs/Spotless 已由 mvn clean verify 验证，启动场景跳过以提速） ----------
echo "[start] 构建 writing-start（含依赖模块）..."
if ! mvn -q -DskipTests -Dpmd.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true \
  -Dspotless.check.skip=true -Ddependency-check.skip=true install; then
  echo "[start] 构建失败——请先运行 mvn clean verify 排查门禁问题" >&2
  exit 1
fi
JAR="writing-start/target/wordpilot.jar"
[ -f "$JAR" ] || { echo "[start] 未找到可执行 jar（构建产物缺失）" >&2; exit 1; }
[ "$BUILD_ONLY" = true ] && { echo "[start] 构建完成: $JAR"; exit 0; }

# ---------- 4. 启动 ----------
echo "[start] 启动 ${JAR}（端口 8080，Ctrl+C 停止）..."
exec java -jar "$JAR"
