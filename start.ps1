<#
.SYNOPSIS
    Script de inicializacao do ms-user (User Service).

.DESCRIPTION
    - Carrega as variaveis de ambiente a partir de um arquivo .env
      (o Spring Boot nao le .env automaticamente).
    - Valida que as variaveis obrigatorias estao definidas.
    - Confere os pre-requisitos (Java 21 e Maven).
    - Sobe o servico em http://localhost:8081.

.PARAMETER EnvFile
    Caminho do arquivo .env a carregar. Padrao: .env (na raiz do projeto).

.PARAMETER Package
    Em vez de 'mvn spring-boot:run', gera o JAR (mvn clean package -DskipTests)
    e executa via 'java -jar'.

.PARAMETER SkipChecks
    Pula a verificacao de pre-requisitos (Java/Maven).

.EXAMPLE
    .\start.ps1
    .\start.ps1 -EnvFile .env.local
    .\start.ps1 -Package
#>
[CmdletBinding()]
param(
    [string]$EnvFile = ".env",
    [switch]$Package,
    [switch]$SkipChecks
)

$ErrorActionPreference = "Stop"

# Trabalha sempre a partir da pasta do script (raiz do projeto)
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Write-Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn2([string]$msg){ Write-Host "[!]  $msg" -ForegroundColor Yellow }
function Fail([string]$msg) {
    Write-Host "[ERRO] $msg" -ForegroundColor Red
    exit 1
}

# Variaveis exigidas pelo application.properties
$Required = @("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "RABBITMQ_ADDRESS")

# ---------------------------------------------------------------------------
# 1. Carrega o .env
# ---------------------------------------------------------------------------
Write-Step "Carregando variaveis de ambiente"

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        # ignora linhas em branco e comentarios
        if ($line -eq "" -or $line.StartsWith("#")) { return }

        # separa apenas no PRIMEIRO '=' (os valores podem conter '=' e '&')
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }

        $name  = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()

        # remove aspas envolventes, se houver
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        Set-Item -Path "env:$name" -Value $value
    }
    Write-Ok "Arquivo '$EnvFile' carregado."
} else {
    Write-Warn2 "Arquivo '$EnvFile' nao encontrado. Usando variaveis ja definidas no ambiente (se houver)."
    Write-Warn2 "Dica: copie .env.example para .env e preencha os valores."
}

# ---------------------------------------------------------------------------
# 2. Valida variaveis obrigatorias
# ---------------------------------------------------------------------------
Write-Step "Validando variaveis obrigatorias"

$missing = @()
foreach ($var in $Required) {
    $val = [Environment]::GetEnvironmentVariable($var, "Process")
    if ([string]::IsNullOrWhiteSpace($val)) { $missing += $var }
}

if ($missing.Count -gt 0) {
    Write-Host ""
    Fail ("Faltando variavel(eis): {0}.`n       Defina-as no arquivo '$EnvFile' ou no ambiente antes de rodar." -f ($missing -join ", "))
}
Write-Ok "Todas as variaveis obrigatorias estao definidas."

# ---------------------------------------------------------------------------
# 3. Pre-requisitos (Java 21 + Maven)
# ---------------------------------------------------------------------------
if (-not $SkipChecks) {
    Write-Step "Verificando pre-requisitos"

    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) { Fail "Java nao encontrado no PATH. Instale o JDK 21." }

    # 'java -version' escreve na saida de erro; capturamos tudo
    $javaVer = (& java -version 2>&1) -join " "
    if ($javaVer -match 'version "?(\d+)') {
        $major = [int]$Matches[1]
        if ($major -lt 21) {
            Write-Warn2 "Java $major detectado; o projeto foi feito para Java 21. Pode haver incompatibilidade."
        } else {
            Write-Ok "Java $major detectado."
        }
    } else {
        Write-Warn2 "Nao foi possivel determinar a versao do Java. Seguindo mesmo assim."
    }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvn) {
        Fail "Maven (mvn) nao encontrado no PATH. Instale o Maven 3.9+ ou rode pela IDE (classe SecrestApplication)."
    }
    Write-Ok "Maven encontrado."
}

# ---------------------------------------------------------------------------
# 4. Sobe o servico
# ---------------------------------------------------------------------------
Write-Host ""
Write-Step "Iniciando o ms-user em http://localhost:8081 (Ctrl+C para parar)"
Write-Host ""

if ($Package) {
    & mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { Fail "Falha no 'mvn package'." }

    $jar = Get-ChildItem -Path "target" -Filter "ms-user-*.jar" |
           Where-Object { $_.Name -notmatch "sources|javadoc" } |
           Select-Object -First 1
    if (-not $jar) { Fail "JAR nao encontrado em target/." }

    & java -jar $jar.FullName
} else {
    & mvn spring-boot:run
}
