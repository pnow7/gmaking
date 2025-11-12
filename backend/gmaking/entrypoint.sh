#!/bin/bash
set -e

echo "Loading environment variables from .env..."

# .env 파일 존재 확인
if [ -f "/app/.env" ]; then
  echo "   → Found /app/.env, exporting variables..."

  # 한 줄씩 읽기
  while IFS= read -r line || [ -n "$line" ]; do
    # CR 제거 및 앞뒤 공백 제거
    line=$(echo "$line" | tr -d '\r' | xargs)

    # 주석(#)이나 빈 줄은 무시
    if [[ -z "$line" ]] || [[ "$line" =~ ^# ]]; then
      continue
    fi

    # KEY=VALUE 구조만 처리
    if [[ "$line" == *"="* ]]; then
      key=$(echo "$line" | cut -d '=' -f 1 | xargs)
      value=$(echo "$line" | cut -d '=' -f 2- | xargs)

      # 키 이름이 올바른 형식(A-Z,a-z,0-9,_)인지 검사
      if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
        export "$key=$value"
      else
        echo "Skipping invalid key: $key"
      fi
    else
      echo "Skipping invalid line (no '='): $line"
    fi
  done < "/app/.env"

  echo ".env variables loaded successfully"
else
  echo "No .env file found at /app/.env — skipping..."
fi

echo "🚀 Starting Spring Boot..."
exec java -jar app.jar
