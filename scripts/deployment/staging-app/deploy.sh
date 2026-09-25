#!/usr/bin/env bash
set -euo pipefail

: "${AWS_REGION:?AWS_REGION is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${STAGING_CLUSTER:?STAGING_CLUSTER is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"

services=(platform-service ticketing-service payment-notification-service gateway)
account_id="$(aws sts get-caller-identity --query Account --output text)"
registry="${account_id}.dkr.ecr.${AWS_REGION}.amazonaws.com"
work_dir="${RUNNER_TEMP}/staging-deploy"
mkdir -p "$work_dir"

# 전체 이미지 확인 후 서비스 순차 배포
for service in "${services[@]}"; do
  aws ecr describe-images \
    --repository-name "tikitaka/staging/${service}" \
    --image-ids "imageTag=${IMAGE_TAG}" \
    --query 'imageDetails[0].imageDigest' \
    --output text > /dev/null
done

for service in "${services[@]}"; do
  image="${registry}/tikitaka/staging/${service}:${IMAGE_TAG}"
  case "$service" in
    platform-service) expected_family=tikitaka-staging-platform ;;
    ticketing-service) expected_family=tikitaka-staging-ticketing ;;
    payment-notification-service) expected_family=tikitaka-staging-payment-notification ;;
    gateway) expected_family=tikitaka-staging-gateway ;;
  esac
  service_json="$(aws ecs describe-services --cluster "$STAGING_CLUSTER" --services "$service")"
  current_arn="$(jq -er '.services[0] | select(.status == "ACTIVE") | .taskDefinition' <<< "$service_json")"
  task_json="$(aws ecs describe-task-definition --task-definition "$current_arn" --query taskDefinition --output json)"
  family="$(jq -er '.family' <<< "$task_json")"
  test "$family" = "$expected_family" || { echo "Unexpected task family: $family" >&2; exit 1; }

  current_image="$(jq -er --arg container "$service" '.containerDefinitions[] | select(.name == $container) | .image' <<< "$task_json")"
  if [ "$current_image" = "$image" ]; then
    echo "${service}: already running ${IMAGE_TAG}" >> "$GITHUB_STEP_SUMMARY"
    continue
  fi

  jq --arg container "$service" --arg image "$image" '
    {
      family, taskRoleArn, executionRoleArn, networkMode, containerDefinitions,
      volumes, placementConstraints, requiresCompatibilities, cpu, memory,
      pidMode, ipcMode, proxyConfiguration, inferenceAccelerators,
      ephemeralStorage, runtimePlatform
    }
    | with_entries(select(.value != null))
    | .containerDefinitions |= map(if .name == $container then .image = $image else . end)
  ' <<< "$task_json" > "${work_dir}/${service}.json"

  new_arn="$(aws ecs register-task-definition \
    --cli-input-json "file://${work_dir}/${service}.json" \
    --query 'taskDefinition.taskDefinitionArn' --output text)"
  aws ecs update-service --cluster "$STAGING_CLUSTER" --service "$service" \
    --task-definition "$new_arn" --output json > /dev/null
  aws ecs wait services-stable --cluster "$STAGING_CLUSTER" --services "$service"

  deployed_arn="$(aws ecs describe-services --cluster "$STAGING_CLUSTER" --services "$service" \
    --query 'services[0].taskDefinition' --output text)"
  test "$deployed_arn" = "$new_arn" || { echo "${service}: unexpected task definition after deployment" >&2; exit 1; }
  echo "${service}: deployed ${IMAGE_TAG}" >> "$GITHUB_STEP_SUMMARY"
done
