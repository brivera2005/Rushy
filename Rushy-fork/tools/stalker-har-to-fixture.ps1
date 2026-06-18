param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [Parameter(Mandatory = $true)]
    [string]$FixtureName,
    [string]$PortalUrl = "https://portal.example.com/c",
    [string]$MacAddress = "00:1A:79:12:34:56",
    [string]$AuthMode = "AUTO",
    [string]$Username = "",
    [string]$Password = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-String {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    return $Value.Trim()
}

function Sanitize-Body {
    param(
        [string]$Body,
        [string]$PortalHost,
        [string]$MacAddress,
        [string]$Username,
        [string]$Password
    )

    $result = $Body
    if ([string]::IsNullOrWhiteSpace($result)) { return "" }

    $result = $result -replace [regex]::Escape($PortalHost), "portal.example.com"
    $result = $result -replace [regex]::Escape($MacAddress), "00:1A:79:12:34:56"

    if (-not [string]::IsNullOrWhiteSpace($Username)) {
        $result = $result -replace [regex]::Escape($Username), "user"
    }
    if (-not [string]::IsNullOrWhiteSpace($Password)) {
        $result = $result -replace [regex]::Escape($Password), "secret"
    }

    $result = [regex]::Replace($result, "(?<=(play_token|token|PHPSESSID|session)=)[A-Za-z0-9._\-]+", "sanitized")
    $result = [regex]::Replace($result, "(?<=""token"":\s*"")[^""]+", "token-sanitized")
    $result = [regex]::Replace($result, "(?<=""mac"":\s*"")[^""]+", "00:1A:79:12:34:56")
    $result = [regex]::Replace($result, "https?://[^/""']+", "https://portal.example.com")
    return $result
}

function Read-HarEntries {
    param([string]$Path)
    $raw = Get-Content -Raw -Path $Path
    $parsed = $raw | ConvertFrom-Json -Depth 100
    if ($null -eq $parsed.log -or $null -eq $parsed.log.entries) {
        throw "Input HAR does not contain log.entries"
    }
    return @($parsed.log.entries)
}

$entries = Read-HarEntries -Path $InputPath
$portalHost = ([Uri]$PortalUrl).Host

$responses = New-Object System.Collections.Generic.List[object]

foreach ($entry in $entries) {
    $requestUrl = Normalize-String $entry.request.url
    if ([string]::IsNullOrWhiteSpace($requestUrl)) { continue }

    try {
        $uri = [Uri]$requestUrl
    } catch {
        continue
    }

    $query = [System.Web.HttpUtility]::ParseQueryString($uri.Query)
    $action = Normalize-String $query["action"]
    if ([string]::IsNullOrWhiteSpace($action)) { continue }

    $method = Normalize-String $entry.request.method
    if ([string]::IsNullOrWhiteSpace($method)) { $method = "GET" }

    $body = ""
    if ($null -ne $entry.response.content -and $null -ne $entry.response.content.text) {
        $body = [string]$entry.response.content.text
    }

    $headers = @{}
    foreach ($header in @($entry.response.headers)) {
        $headerName = Normalize-String $header.name
        if ($headerName -eq "Set-Cookie") {
            $headers[$headerName] = "PHPSESSID=sanitized; Path=/; HttpOnly"
        }
    }

    $responseRecord = [ordered]@{
        action = $action
        method = $method.ToUpperInvariant()
        code = [int]$entry.response.status
        body = (Sanitize-Body -Body $body -PortalHost $portalHost -MacAddress $MacAddress -Username $Username -Password $Password)
    }

    if ($headers.Count -gt 0) {
        $responseRecord["headers"] = $headers
    }

    $responses.Add([pscustomobject]$responseRecord)
}

$fixture = [ordered]@{
    name = $FixtureName
    device = [ordered]@{
        portalUrl = $PortalUrl
        macAddress = $MacAddress
        authMode = $AuthMode
    }
    responses = $responses
    expected = [ordered]@{
        authMode = $AuthMode
        portalProfile = "MAG_BASIC"
        bootstrapEvidence = @()
        requestOrder = @($responses | ForEach-Object { $_.action })
    }
}

if (-not [string]::IsNullOrWhiteSpace($Username)) {
    $fixture.device["username"] = "user"
}
if (-not [string]::IsNullOrWhiteSpace($Password)) {
    $fixture.device["password"] = "secret"
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and -not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$fixture | ConvertTo-Json -Depth 20 | Set-Content -Path $OutputPath -Encoding UTF8
