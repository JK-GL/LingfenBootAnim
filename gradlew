#!/bin/sh
# Gradle wrapper 引导脚本（CI 里用 gradle/actions/setup-gradle 时可有可无）
if [ -n "$GRADLE_HOME" ] && [ -x "$GRADLE_HOME/bin/gradle" ]; then
  exec "$GRADLE_HOME/bin/gradle" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "未找到 Gradle，请使用 gradle/actions/setup-gradle 或本地安装" >&2
exit 1
