#!/usr/bin/env bash
# ============================================================================
# wordpilot 全生命周期管理脚本（后端 + 前端）
#
# 职责：
#   1. 载入 .env（DB_PASSWORD / DASHSCOPE_API_KEY / BGE_MODEL_PATH，不入版本库）
#   2. 校验凭证、中间件端口（MySQL 3309 / Redis 6379 / ES 9200）、node 环境
#   3. 轻量构建后端 fat jar（静态门禁已由 mvn clean verify 验证，启动场景跳过）
#   4. 守护运行后端(8080) + 前端 Vite(5173)，支持 start/stop/restart/status
#
# 用法：
#   ./start.sh               # 等价 start
#   ./start.sh start         # 启动后端(8080)+前端(5173)，守护运行，日志落 .run/
#   ./start.sh stop          # 停止前后端（按 PID 文件 + 端口兜底）
#   ./start.sh restart       # stop + start
#   ./start.sh status        # 显示前后端 PID/端口/健康状态
#   ./start.sh build         # 仅构建（后端 jar + 前端 dist），不启动
#   ./start.sh check         # 仅检查凭证/中间件/node，不启动
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")"

RUN_DIR="$(pwd)/.run"
FRONTEND_DIR="frontend"
mkdir -p "$RUN_DIR"

# ---------- 0. 参数解析 ----------
CMD="${1:-start}"
case "$CMD" in
  -b) CMD=build ;;
  -l) CMD=check ;;
esac
case "$CMD" in
  start|stop|restart|status|build|check) ;;
  *) echo "未知命令: $CMD（支持 start / stop / restart / status / build / check）" >&2; exit 2 ;;
esac

# ---------- 1. 载入 .env ----------
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
else
  echo "[wordpilot] 未找到 .env，将仅使用已导出的环境变量（凭证缺失时会报错）" >&2
fi

# ---------- 2. 公共校验 ----------
check_env() {
  : "${DB_PASSWORD:?缺少 DB_PASSWORD（在 .env 或环境变量中设置）}"
  : "${DASHSCOPE_API_KEY:?缺少 DASHSCOPE_API_KEY（在 .env 或环境变量中设置）}"
  : "${BGE_MODEL_PATH:?缺少 BGE_MODEL_PATH（在 .env 或环境变量中设置）}"
  [ -d "$BGE_MODEL_PATH" ] || { echo "[wordpilot] BGE 模型目录不存在: $BGE_MODEL_PATH" >&2; return 1; }
  echo "[wordpilot] 凭证就绪: DB_PASSWORD=*** DASHSCOPE_API_KEY=sk-${DASHSCOPE_API_KEY:8}*** BGE=$BGE_MODEL_PATH"
  for spec in "3309 MySQL" "6379 Redis" "9200 Elasticsearch"; do
    port="${spec%% *}"; name="${spec#* }"
    if nc -z -w 2 127.0.0.1 "$port" 2>/dev/null; then
      echo "[wordpilot] 中间件 OK: $name($port)"
    else
      echo "[wordpilot] 中间件不可达: $name($port) —— 请先启动 $name" >&2
      return 1
    fi
  done
  command -v node >/dev/null 2>&1 || { echo "[wordpilot] 未找到 node（请先安装 Node 18+）" >&2; return 1; }
  command -v npm >/dev/null 2>&1 || { echo "[wordpilot] 未找到 npm（请随 Node 安装）" >&2; return 1; }
}

# ---------- 3. 构建 ----------
build() {
  echo "[wordpilot] 构建后端 fat jar..."
  if ! mvn -q -DskipTests -Dpmd.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true \
    -Dspotless.check.skip=true -Ddependency-check.skip=true install; then
    echo "[wordpilot] 后端构建失败——请先运行 mvn clean verify 排查门禁问题" >&2
    return 1
  fi
  [ -f "writing-start/target/wordpilot.jar" ] || { echo "[wordpilot] 未找到可执行 jar（构建产物缺失）" >&2; return 1; }
  echo "[wordpilot] 准备前端依赖..."
  if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    ( cd "$FRONTEND_DIR" && npm ci ) || { echo "[wordpilot] 前端依赖安装失败" >&2; return 1; }
  fi
  echo "[wordpilot] 构建前端 dist..."
  ( cd "$FRONTEND_DIR" && npm run build ) || { echo "[wordpilot] 前端构建失败" >&2; return 1; }
  echo "[wordpilot] 构建完成: writing-start/target/wordpilot.jar + $FRONTEND_DIR/dist"
}

# ---------- 4. 进程管理 ----------
pid_alive() { kill -0 "$1" 2>/dev/null; }

stop_one() {
  local port="$1" pidfile="$2" label="$3"
  local pid
  pid=$(cat "$pidfile" 2>/dev/null || true)
  if [ -n "$pid" ] && pid_alive "$pid"; then
    kill "$pid" 2>/dev/null || true
    echo "[wordpilot] 已停止 $label (PID $pid)"
  fi
  # 端口兜底（进程树/残留）
  local leaked
  leaked=$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
  if [ -n "$leaked" ]; then
    printf '%s\n' "$leaked" | xargs -r kill -9 2>/dev/null || true
    echo "[wordpilot] 已清理 $label 残留进程 (端口 $port)"
  fi
  rm -f "$pidfile"
}

stop() {
  echo "[wordpilot] 停止后端与前端..."
  stop_one 8080 "$RUN_DIR/backend.pid" 后端
  stop_one 5173 "$RUN_DIR/frontend.pid" 前端
  echo "[wordpilot] 已停止"
}

start() {
  check_env || return 1
  build || return 1
  local jar="writing-start/target/wordpilot.jar"

  # 后端守护
  echo "[wordpilot] 启动后端 $jar (端口 8080)..."
  nohup java -jar "$jar" > "$RUN_DIR/backend.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"

  # 前端守护
  echo "[wordpilot] 启动前端 Vite dev (端口 5173)..."
  ( cd "$FRONTEND_DIR" && nohup npm run dev > "$RUN_DIR/frontend.log" 2>&1 & echo $! > "$RUN_DIR/frontend.pid" )

  # 等待就绪
  echo "[wordpilot] 等待服务就绪（后端 BGE 模型加载约 20s）..."
  local ok_backend=false ok_frontend=false
  for _ in $(seq 1 45); do
    if [ "$ok_backend" = false ] && curl -s -m 2 -o /dev/null http://127.0.0.1:8080/api/health 2>/dev/null; then
      echo "[wordpilot] 后端就绪: http://localhost:8080 (PID $(cat "$RUN_DIR/backend.pid"))"
      ok_backend=true
    fi
    if [ "$ok_frontend" = false ] && curl -s -m 2 -o /dev/null http://localhost:5173/ 2>/dev/null; then
      echo "[wordpilot] 前端就绪: http://localhost:5173"
      ok_frontend=true
    fi
    [ "$ok_backend" = true ] && [ "$ok_frontend" = true ] && break
    sleep 1
  done
  if [ "$ok_backend" = true ]; then
    echo "[wordpilot] 启动完成（日志: $RUN_DIR/backend.log / $RUN_DIR/frontend.log）"
  else
    echo "[wordpilot] 后端未就绪，请查看 $RUN_DIR/backend.log" >&2
    return 1
  fi
}

status() {
  local bpid fpid
  bpid=$(cat "$RUN_DIR/backend.pid" 2>/dev/null || true)
  fpid=$(cat "$RUN_DIR/frontend.pid" 2>/dev/null || true)
  echo "=== 后端 (8080) ==="
  if [ -n "$bpid" ] && pid_alive "$bpid"; then
    echo "  PID: $bpid (存活)"
    lsof -iTCP:8080 -sTCP:LISTEN -n -P 2>/dev/null | tail -1 | awk '{print "  监听: "$9}'
    curl -s -m 3 http://127.0.0.1:8080/api/health 2>/dev/null | grep -q '"status":"UP"' \
      && echo "  健康: UP" || echo "  健康: 未响应"
  else
    echo "  未运行"
  fi
  echo "=== 前端 (5173) ==="
  if [ -n "$fpid" ] && pid_alive "$fpid"; then
    echo "  PID: $fpid (存活)"
    lsof -iTCP:5173 -sTCP:LISTEN -n -P 2>/dev/null | tail -1 | awk '{print "  监听: "$9}'
    curl -s -m 3 -o /dev/null -w '  HTTP: %{http_code}\n' http://localhost:5173/ 2>/dev/null || echo "  HTTP: 未响应"
  else
    echo "  未运行"
  fi
}

# ---------- 5. 分发 ----------
case "$CMD" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; start ;;
  status)  status ;;
  build)   check_env >/dev/null 2>&1 || true; build ;;
  check)   check_env && echo "[wordpilot] 检查通过，未启动应用" ;;
esac
