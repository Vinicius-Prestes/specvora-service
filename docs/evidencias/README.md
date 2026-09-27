# Evidências das varreduras

| Arquivo | Conteúdo |
|---|---|
| `semgrep-scan.txt` | Saída do Semgrep 1.90.0 com `p/owasp-top-ten` + `p/java` e `--error` (0 achados) |
| `trufflehog-only-verified.txt` | Saída do TruffleHog 3.97.9 com `--only-verified` sobre todo o histórico Git (0 segredos verificados) |

Adicione aqui os **prints do GitHub Actions** após o push:

- `01-pipeline-visao-geral.png` — tela do workflow com todos os jobs verdes
- `02-job-secret-scanning.png` — log do TruffleHog / Gitleaks
- `03-job-sast.png` — log do Semgrep
- `04-code-scanning.png` — aba **Security > Code scanning** com os SARIF
