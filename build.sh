#!/usr/bin/env bash
# Sobe o back com Java 17 sem mexer no Java padrão da máquina.
# Uso: ./build.sh            sobe a API
#      ./build.sh build      limpa, compila, testa e gera o .jar
#      ./build.sh <...>      qualquer outro argumento vai pro Maven (ex.: clean spring-boot:run)
set -e

# Pasta onde está este script (equivale ao $PSScriptRoot).
DIR="$(cd "$(dirname "$0")" && pwd)"

# Usa JDK17_HOME se a pessoa definir; senão procura um JDK 17 no lugar padrão do Linux.
JDK="${JDK17_HOME:-$(ls -d /usr/lib/jvm/*17* 2>/dev/null | head -n 1)}"

if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then
    echo "JDK 17 não encontrado. Instale (ex.: sudo apt install openjdk-17-jdk) ou defina JDK17_HOME." >&2
    exit 1
fi

# Vale só para este processo, igual no .ps1.
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
echo "Usando Java de $JAVA_HOME"

if ! command -v docker > /dev/null 2>&1; then
    echo "Docker não está instalado. Veja: https://docs.docker.com/engine/install/" >&2
    exit 1
fi

# Se o Docker estiver parado, liga sozinho (igual o .ps1 abre o Docker Desktop).
if ! docker info > /dev/null 2>&1; then
    echo "Ligando o Docker (pode pedir sua senha)..."
    sudo systemctl start docker
    for _ in $(seq 1 20); do
        docker info > /dev/null 2>&1 && break
        sleep 3
    done
    if ! docker info > /dev/null 2>&1; then
        echo "O Docker não respondeu. Se ele estiver ligado, falta pôr seu usuário no grupo docker:" >&2
        echo "  sudo usermod -aG docker \$USER   (depois saia e entre de novo na sessão)" >&2
        exit 1
    fi
    echo "Docker pronto."
fi

# Docker novo usa "docker compose"; instalações antigas só têm "docker-compose".
if docker compose version > /dev/null 2>&1; then
    docker compose -f "$DIR/docker-compose.yaml" up -d
else
    docker-compose -f "$DIR/docker-compose.yaml" up -d
fi

# Sem argumentos, sobe a API.
# "build": limpa, compila, roda os testes e gera o .jar em target/ (não sobe a API).
# Qualquer outra coisa vai direto pro Maven.
if [ $# -eq 0 ]; then
    set -- spring-boot:run
elif [ "$1" = "build" ]; then
    shift
    set -- clean package "$@"
fi

# Garante que o mvnw pode ser executado (o clone às vezes vem sem essa permissão).
chmod +x "$DIR/mvnw"
exec "$DIR/mvnw" "$@"
