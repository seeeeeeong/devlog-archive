#!/bin/bash
# 배포 스크립트 (EC2에서 실행)
# 사용법: bash /home/ec2-user/app/deploy.sh <ECR_IMAGE>
set -euo pipefail

NEW_IMAGE=$1
APP_DIR=/home/ec2-user/app
COMPOSE_FILE=docker-compose-prod.yml
ENV_FILE=$APP_DIR/.env.prod
SSM_PARAMETER_PREFIX="${SSM_PARAMETER_PREFIX:-/devlog-archive/prod}"
SSM_AWS_REGION="${SSM_AWS_REGION:-ap-northeast-2}"

required_vars=(
  DB_PASSWORD
  OPENAI_API_KEY
  ALLOWED_ORIGIN
)

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose -f "$COMPOSE_FILE")
else
  echo "docker compose command not found."
  exit 1
fi

fetch_ssm_parameter() {
  local name=$1
  local value=""

  if ! value=$(aws ssm get-parameter \
    --region "$SSM_AWS_REGION" \
    --name "$name" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text 2>/dev/null); then
    return 1
  fi

  if [ "$value" = "None" ] || [ -z "$value" ]; then
    return 1
  fi

  printf '%s' "$value"
}

load_runtime_env() {
  local missing_vars=()

  for var_name in "${required_vars[@]}"; do
    if [ -n "${!var_name:-}" ]; then
      continue
    fi

    local param_name="$SSM_PARAMETER_PREFIX/$var_name"
    local value=""

    if value=$(fetch_ssm_parameter "$param_name"); then
      export "$var_name=$value"
    else
      missing_vars+=("$var_name")
    fi
  done

  if [ "${#missing_vars[@]}" -gt 0 ]; then
    echo "Missing required env vars from SSM [$SSM_PARAMETER_PREFIX]: ${missing_vars[*]}"
    exit 1
  fi
}

write_env_file() {
  cat > "$ENV_FILE" <<EOV
DB_PASSWORD=${DB_PASSWORD}
OPENAI_API_KEY=${OPENAI_API_KEY}
ALLOWED_ORIGIN=${ALLOWED_ORIGIN}
EOV

  chmod 600 "$ENV_FILE"
  chown ec2-user:ec2-user "$ENV_FILE" 2>/dev/null || true

  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

echo "=== Deploy ==="
echo "New image: $NEW_IMAGE"

# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin "$(echo "$NEW_IMAGE" | cut -d/ -f1)"

cd "$APP_DIR"

load_runtime_env
write_env_file

# DB 먼저 기동 (없으면 최초 실행)
if ! docker inspect devlog-postgres >/dev/null 2>&1; then
  echo "Bootstrap: starting database..."
  "${COMPOSE[@]}" up -d db
  echo "Waiting for DB to be ready..."
  sleep 15
fi

# 새 이미지로 앱 재시작
APP_UP_LOG=/tmp/deploy-app-up.log
set +e
ECR_IMAGE="$NEW_IMAGE" "${COMPOSE[@]}" up -d --no-deps app >"$APP_UP_LOG" 2>&1
APP_UP_EXIT=$?
set -e

if [ "$APP_UP_EXIT" -ne 0 ]; then
  echo "Failed to start app."
  tail -n 200 "$APP_UP_LOG" || true
  exit 1
fi

# Caddy 기동
"${COMPOSE[@]}" up -d --no-deps caddy >/dev/null 2>&1

# Health check 대기
HEALTH_CHECK_RETRIES=48
HEALTH_CHECK_INTERVAL=5

echo "Waiting for app to be healthy..."
for i in $(seq 1 "$HEALTH_CHECK_RETRIES"); do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' "devlog-archive" 2>/dev/null || echo "unknown")
  echo "  [$i/$HEALTH_CHECK_RETRIES] status=$STATUS"
  if [ "$STATUS" = "healthy" ]; then
    echo "Health check passed."
    break
  fi
  if [ "$i" = "$HEALTH_CHECK_RETRIES" ]; then
    echo "Health check timed out."
    docker logs --tail 200 devlog-archive 2>/dev/null || true
    exit 1
  fi
  sleep "$HEALTH_CHECK_INTERVAL"
done

# 오래된 이미지 정리
docker image prune -f

echo "=== Deployed successfully ==="
