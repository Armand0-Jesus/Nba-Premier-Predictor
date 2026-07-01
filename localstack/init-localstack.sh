#!/bin/sh
set -eu

awslocal --endpoint-url=http://localstack:4566 s3 mb s3://nba-premier-prediction-reports || true
awslocal --endpoint-url=http://localstack:4566 sqs create-queue --queue-name nba-premier-async-jobs >/dev/null
