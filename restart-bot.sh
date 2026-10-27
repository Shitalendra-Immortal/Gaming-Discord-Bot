#!/bin/bash
# Restart LEGEND on this machine. The bot token is NOT stored here: it must
# be supplied as the DISCORD_TOKEN environment variable when this script runs.
# Also refreshes the egress proxy CA in the home JDK truststore, since the
# platform CA rotates on runtime restarts and a stale CA breaks TLS.
set -e
cd "$(dirname "$0")"
KEYTOOL="$HOME/jdk17/bin/keytool"
CACERTS="$HOME/jdk17/lib/security/cacerts"
CA_FILE="/usr/local/share/ca-certificates/hatch-egress-ca.crt"
if [ -f "$CA_FILE" ]; then
    "$KEYTOOL" -delete -alias hatch-egress-ca -keystore "$CACERTS" -storepass changeit 2>/dev/null || true
    "$KEYTOOL" -import -trustcacerts -noprompt -alias hatch-egress-ca \
        -file "$CA_FILE" -keystore "$CACERTS" -storepass changeit 2>/dev/null || true
fi
exec "$HOME/jdk17/bin/java" \
    -Dhttps.proxyHost=hatch-egress-proxy -Dhttps.proxyPort=3128 \
    -Dhttp.proxyHost=hatch-egress-proxy -Dhttp.proxyPort=3128 \
    -Dhttp.nonProxyHosts='localhost|127.0.0.1' \
    -cp 'classes:libs/*' com.gamingbot.BotMain
