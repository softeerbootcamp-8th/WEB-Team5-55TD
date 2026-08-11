# QA GitOps bootstrap

`for-qa`의 애플리케이션 변경은 `.github/workflows/qa-gitops.yml`에서 두 이미지를
GHCR에 커밋 SHA 태그로 발행하고 QA Kustomize overlay를 갱신합니다. Argo CD는 해당
overlay를 자동 동기화합니다.

## 최초 1회 설치

Argo CD 설치 후 project와 application을 등록합니다. 설치 버전은 운영 시점에 검증한
버전으로 고정하고, 원격 `stable` URL을 그대로 자동 실행하지 마세요.

```sh
kubectl create namespace argocd
kubectl apply -n argocd -f <검증하고 버전을 고정한 Argo-CD install.yaml>
kubectl apply -f argocd/project.yaml
kubectl apply -f argocd/application-qa.yaml
```

비공개 저장소라면 Argo CD repository credential을 별도로 등록해야 합니다.
애플리케이션 시작 전 아래 Secret도 클러스터에 생성합니다(값은 Git에 커밋하지 않음).

```sh
kubectl -n pickup-qa create secret generic pickup-api-secret \
  --from-literal=DB_URL='jdbc:mysql://...' \
  --from-literal=DB_USERNAME='...' \
  --from-literal=DB_PASSWORD='...' \
  --from-literal=JWT_SECRET='...' \
  --from-literal=REDIS_HOST='...' \
  --from-literal=REDIS_PORT='6379' \
  --from-literal=WEBSOCKET_ALLOWED_ORIGINS='https://qa.example.com'
```

GHCR 패키지가 비공개라면 `pickup-qa`에 pull secret을 만든 뒤 base Deployment에
`imagePullSecrets`를 추가하거나, 클러스터 정책으로 해당 ServiceAccount에 연결합니다.
GitHub의 Actions 설정에서 workflow `Read and write permissions`를 허용하고,
`for-qa` 보호 규칙이 있다면 GitHub Actions bot의 overlay 갱신을 허용해야 합니다.
