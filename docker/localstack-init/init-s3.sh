#!/bin/sh
# LocalStack corre este script solo cuando ya esta "ready" (hook oficial
# /etc/localstack/init/ready.d/). Crea el bucket que S3DocumentStorageGateway
# espera encontrar - LocalStack no lo crea solo.
set -e
awslocal s3 mb "s3://${HRSAAS_S3_BUCKET:-hrsaas-documents}" || true
