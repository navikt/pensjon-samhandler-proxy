#!/usr/bin/env bash

if [[ $# -eq 0 ]]; then
    env="q2"

elif [[ $# -eq 1 ]]; then
    env=$1

else
    echo "Usage: "
    echo "    No input: fetch secrets for q2"
    echo "    <env>"

    exit 1
fi

envfile=.env

bold=$(tput bold)
normal=$(tput sgr0)
white="[97;1m"
endcolor="[0m"

if command -v nais >& /dev/null; then
  DISCONNECT_STATUS=$(nais device status | grep -c Disconnected)

  if [ $DISCONNECT_STATUS -eq 1 ]; then
    read -p "Du er ikke koblet til med naisdevice. Vil du koble til? (j/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[YyjJ]$ ]]; then
      nais device connect
    else
      echo -e "${red}Du må være koblet til med naisdevice, avslutter${endcolor}"
      exit 1
    fi
  fi
fi

command -v vault >& /dev/null || (
  echo "ERROR: You need to install the Vault CLI on your machine: https://www.vaultproject.io/downloads.html" && exit 1
) || exit 1
command -v jq >& /dev/null || (
  echo "ERROR: You need to install the jq CLI tool on your machine: https://stedolan.github.io/jq/" && exit 1
) || exit 1
command -v base64 >& /dev/null || (
  echo "ERROR: You need to install the base64 tool on your machine. (brew install base64 on macOS)" && exit 1
) || exit 1
command -v kubectl >& /dev/null || (
  echo "ERROR: You need to install and configure kubectl (see: https://confluence.adeo.no/x/UzjYF)" && exit 1
) || exit 1

export VAULT_ADDR=https://vault.adeo.no

while true; do
  NAME="$(vault token lookup -format=json | jq '.data.display_name' -r; exit ${PIPESTATUS[0]})"
  ret=${PIPESTATUS[0]}
  if [ "$ret" -ne 0 ]; then
    echo "Looks like you are not logged in to Vault."

    read -p "Do you want to log in? (y/n) " -n 1 -r
    echo    # (optional) move to a new line
    if [[ $REPLY =~ ^[YyJj]$ ]]
    then
      vault login -method=oidc -no-print
    else
      echo "Could not log in to Vault. Aborting."
      exit 1
    fi
  else
    break;
  fi
done

set -e
set -o pipefail

function fetch_kubernetes_secrets {
    local type=$1
    local context=$2
    local namespace=$3
    local secret=$4
    local mode=$5
    local A=("$@")

    echo -n -e "\t- $type "

    local context_namespace_secrets_value=$(kubectl --context="$context" -n "$namespace" get secrets)

    if [[ "mode" == "strict" ]]; then
        local secret_name=$(echo "$context_namespace_secrets_value" | awk "/$secret/ {print \$1}")
    else
        local secret_name=$(echo "$context_namespace_secrets_value" | grep "$secret" | tail -1 | awk '{print $1}')
    fi

    if [[ $secret_name == *$'\n'* ]]; then
       echo
       echo "Fant følgende hemmeligheter som samsvarte med søkestrengen \"$secret\". Støtter kun en hemmelighet"
       echo $secret_name
       exit 1
    fi

    local secret_response=$(kubectl --context="$context" -n "$namespace" get secret "$secret_name" -o json)

    for name in "${A[@]:5}"
    do
        {
          echo -n "$name='"
          echo "$secret_response" | jq -j ".data[\"$name\"]" | base64 --decode |  tr -d '\n'
          echo "'"
        } >> ${envfile}
    done

    echo -e "${bold}${white}✔${endcolor}${normal}"
}


rm -f "${envfile}"
touch "${envfile}"


echo -e "${bold}Henter secrets fra Kubernetes${normal}"

fetch_kubernetes_secrets "AzureAD" "dev-fss" "pensjon-${env}" "azure-pensjon-samhandler-proxy-$env" "strict" \
    "AZURE_APP_CLIENT_ID" \
    "AZURE_OPENID_CONFIG_ISSUER"

echo

echo "${bold}Henter secrets fra Vault${normal}"

echo -n -e "\t- Servicebruker "

echo "SRVPENMQ_USERNAME='$(vault kv get -field username -mount=serviceuser dev/srvpenmq)'" >> "${envfile}"
echo "SRVPENMQ_PASSWORD='$(vault kv get -field password -mount=serviceuser dev/srvpenmq)'" >> "${envfile}"

echo -e "${bold}${white}✔${endcolor}${normal}"

echo "SPRING_PROFILES_ACTIVE='${env}'" >> "${envfile}"

echo

echo "Hentet hemmeligheter og oppdatert filen ${bold}$(realpath ${envfile})${normal}"
