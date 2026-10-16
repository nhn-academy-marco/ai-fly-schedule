#!/usr/bin/env bash
#
# 서버에서 애플리케이션을 (재)시작합니다. GitHub Actions(maven.yml)가 배포할 때마다 실행합니다.
#
# 필요한 환경 변수
#   DATA_GO_KR_SERVICE_KEY  (필수) 공공데이터 포털 서비스 키 - workflow가 GitHub Secrets에서 넘겨줌
#   GOOGLE_GENAI_API_KEY    (선택) Gemini API 키 - 서버의 ~/fly-schedule/.env에 적어 두면 읽음
#   SERVER_PORT             (선택) 기본 10600 (nginx가 fly.java21.net을 넘기는 포트)
#   SPRING_PROFILES_ACTIVE  (선택) 기본 prod
#   JAVA_BIN                (선택) 기본 /usr/local/java/java21/bin/java, 없으면 JAVA_HOME → PATH의 java
#   JAVA_OPTS               (선택) 기본 "-Xms512m -Xmx1024m"
set -euo pipefail

APP_DIR="$HOME/fly-schedule"
LOG_FILE="$APP_DIR/app.log"
PID_FILE="$APP_DIR/app.pid"

# scp-action은 디렉터리 구조를 유지하므로 jar는 target/fly.jar에 놓인다
JAR="$APP_DIR/target/fly.jar"
[ -f "$JAR" ] || JAR="$APP_DIR/fly.jar"
[ -f "$JAR" ] || { echo "[startup] jar 파일이 없습니다: $APP_DIR/target/fly.jar"; exit 1; }

# 서버에 직접 둔 선택 설정 (예: GOOGLE_GENAI_API_KEY=...)
if [ -f "$APP_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$APP_DIR/.env"
  set +a
fi

: "${DATA_GO_KR_SERVICE_KEY:?[startup] DATA_GO_KR_SERVICE_KEY가 없습니다. GitHub Secrets 또는 $APP_DIR/.env를 확인하세요.}"
export DATA_GO_KR_SERVICE_KEY
[ -n "${GOOGLE_GENAI_API_KEY:-}" ] && export GOOGLE_GENAI_API_KEY

# 포트 결정: SERVER_PORT → 원래 startup.sh(.orig)에 적힌 포트 → nginx의 proxy_pass 포트 → 10600
# (nginx가 바라보는 포트와 다르면 502 Bad Gateway가 나므로, 서버 설정에서 포트를 찾는다)
NGINX_HOST="${NGINX_SERVER_NAME:-fly.java21.net}"
detect_port() {
  if [ -n "${SERVER_PORT:-}" ]; then
    echo "$SERVER_PORT env"; return
  fi
  local found
  if [ -f "$APP_DIR/startup.sh.orig" ]; then
    # "--server.port=10600" 또는 "PORT=10600" (--server.port=$PORT처럼 변수를 쓰는 경우)
    found=$(grep -oE '(server\.port[= ]|SERVER_PORT=|^[[:space:]]*PORT=)[0-9]+' "$APP_DIR/startup.sh.orig" | grep -oE '[0-9]+$' | head -1 || true)
    [ -n "$found" ] && { echo "$found startup.sh.orig"; return; }
  fi
  local conf target upstream
  for conf in $(grep -rlE "server_name[^;]*$NGINX_HOST" /etc/nginx/conf.d /etc/nginx/sites-enabled 2>/dev/null || true); do
    # proxy_pass http://127.0.0.1:8000; 처럼 포트가 바로 적힌 경우
    target=$(grep -oE 'proxy_pass[[:space:]]+https?://[^;[:space:]]+' "$conf" | head -1 | sed -E 's#proxy_pass[[:space:]]+https?://##' || true)
    found=$(echo "$target" | grep -oE ':[0-9]+' | tr -d ':' | head -1 || true)
    [ -n "$found" ] && { echo "$found nginx($conf)"; return; }
    # proxy_pass http://fly_backend; 처럼 upstream 이름을 쓰는 경우
    upstream="${target%%/*}"
    if [ -n "$upstream" ]; then
      found=$(grep -rhA5 -E "upstream[[:space:]]+$upstream[[:space:]]*\{" /etc/nginx 2>/dev/null \
        | grep -oE 'server[[:space:]]+[^;]+:[0-9]+' | grep -oE '[0-9]+$' | head -1 || true)
      [ -n "$found" ] && { echo "$found nginx-upstream($upstream)"; return; }
    fi
  done
  echo "10600 default"
}
read -r PORT PORT_SOURCE < <(detect_port)
echo "[startup] 포트 $PORT (근거: $PORT_SOURCE)"
# 502 진단용: nginx가 이 도메인을 어디로 넘기는지 출력 (키 같은 비밀 값은 들어 있지 않음)
grep -rnE "server_name[^;]*$NGINX_HOST|proxy_pass|upstream|^[[:space:]]*server[[:space:]]+[^;]+:[0-9]+" \
  /etc/nginx/conf.d /etc/nginx/sites-enabled 2>/dev/null | sed 's/^/[startup][nginx] /' | head -20 || true
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"

# 비대화식 SSH에서는 PATH의 java가 21이 아닐 수 있으므로 서버에 설치된 Java 21을 우선 사용
if [ -z "${JAVA_BIN:-}" ]; then
  if [ -x /usr/local/java/java21/bin/java ]; then
    JAVA_BIN=/usr/local/java/java21/bin/java
  elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  else
    JAVA_BIN=java
  fi
fi
echo "[startup] Java: $JAVA_BIN ($("$JAVA_BIN" -version 2>&1 | head -1))"

# 1. 실행 중인 애플리케이션 종료
stop_pid() {
  local pid="$1"
  kill "$pid" 2>/dev/null || return 0
  for _ in $(seq 1 30); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  echo "[startup] 30초 안에 종료되지 않아 강제 종료합니다 (pid=$pid)"
  kill -9 "$pid" 2>/dev/null || true
}

if [ -f "$PID_FILE" ]; then
  stop_pid "$(cat "$PID_FILE")"
  rm -f "$PID_FILE"
fi
# PID 파일 없이 실행된 이전 프로세스(옛 startup.sh로 띄운 것)도 정리
for pid in $(pgrep -f "java.* -jar .*fly\.jar" || true); do
  stop_pid "$pid"
done
# 같은 포트를 잡고 있는 프로세스가 남아 있으면 새 앱이 뜨지 못하므로 정리
if command -v lsof >/dev/null; then
  for pid in $(lsof -t -i:"$PORT" -sTCP:LISTEN 2>/dev/null || true); do
    echo "[startup] 포트 $PORT를 사용 중인 프로세스 종료 (pid=$pid)"
    stop_pid "$pid"
  done
fi

# 2. 새로 시작 (SSH 세션이 끝나도 계속 실행되도록 nohup, Linux에서는 setsid로 세션도 분리)
DETACH=""
command -v setsid >/dev/null && DETACH="setsid"
echo "[startup] 시작: $JAR (port=$PORT, profile=$PROFILE)"
# shellcheck disable=SC2086
$DETACH nohup "$JAVA_BIN" $JAVA_OPTS -jar "$JAR" --server.port="$PORT" --spring.profiles.active="$PROFILE" \
  >> "$LOG_FILE" 2>&1 < /dev/null &
echo $! > "$PID_FILE"

# 3. 기동 확인 (최대 90초)
for _ in $(seq 1 90); do
  if curl -fs -o /dev/null "http://localhost:$PORT/"; then
    echo "[startup] 기동 완료 (pid=$(cat "$PID_FILE"))"
    exit 0
  fi
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    break
  fi
  sleep 1
done

echo "[startup] 기동 실패. 최근 로그:"
tail -n 50 "$LOG_FILE"
exit 1
