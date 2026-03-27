param(
    [string]$BaseUrl = "http://127.0.0.1:8080/Game",
    [int]$TimeoutSeconds = 10
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Convert-BaseUrlToWebSocketUrl {
    param([string]$Url)

    $uri = [Uri]$Url
    $scheme = if ($uri.Scheme -eq "https") { "wss" } else { "ws" }
    return "{0}://{1}{2}" -f $scheme, $uri.Authority, $uri.AbsolutePath.TrimEnd("/")
}

function Send-WebSocketText {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [string]$Text,
        [int]$Seconds
    )

    $buffer = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $segment = [System.ArraySegment[byte]]::new($buffer)
    $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($Seconds))
    try {
        $Socket.SendAsync(
            $segment,
            [System.Net.WebSockets.WebSocketMessageType]::Text,
            $true,
            $cts.Token
        ).GetAwaiter().GetResult()
    } finally {
        $cts.Dispose()
    }
}

function Receive-WebSocketText {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [int]$Seconds
    )

    $buffer = New-Object byte[] 4096
    $builder = New-Object System.Text.StringBuilder
    $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($Seconds))
    try {
        do {
            $segment = [System.ArraySegment[byte]]::new($buffer)
            $result = $Socket.ReceiveAsync($segment, $cts.Token).GetAwaiter().GetResult()
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                throw "WebSocket closed before a full text frame was received."
            }
            if ($result.Count -gt 0) {
                [void]$builder.Append([System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
            }
        } while (-not $result.EndOfMessage)

        return $builder.ToString()
    } finally {
        $cts.Dispose()
    }
}

function Close-WebSocketQuietly {
    param([System.Net.WebSockets.ClientWebSocket]$Socket)

    if ($null -eq $Socket) {
        return
    }

    try {
        if ($Socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(2))
            try {
                $Socket.CloseAsync(
                    [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                    "smoke-test",
                    $cts.Token
                ).GetAwaiter().GetResult()
            } finally {
                $cts.Dispose()
            }
        }
    } catch {
    } finally {
        $Socket.Dispose()
    }
}

function Assert-Contains {
    param(
        [string]$Text,
        [string]$Expected,
        [string]$Message
    )

    if ($Text -notlike "*$Expected*") {
        throw "$Message Actual: $Text"
    }
}

$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$baseWsUrl = Convert-BaseUrlToWebSocketUrl -Url $normalizedBaseUrl

Write-Host "Checking SockJS info endpoint..."
$infoUrl = "$normalizedBaseUrl/ws/info?t=$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$infoResponse = Invoke-WebRequest -Uri $infoUrl -TimeoutSec $TimeoutSeconds -UseBasicParsing
if ($infoResponse.StatusCode -ne 200) {
    throw "SockJS info endpoint returned unexpected status code: $($infoResponse.StatusCode)"
}
Assert-Contains -Text $infoResponse.Content -Expected '"websocket":true' -Message "SockJS info payload is missing websocket support flag."

Write-Host "Checking SockJS websocket transport + STOMP CONNECT..."
$sockJsSocket = [System.Net.WebSockets.ClientWebSocket]::new()
try {
    $sockJsSessionId = [Guid]::NewGuid().ToString("N")
    $sockJsUri = [Uri]"$baseWsUrl/ws/000/$sockJsSessionId/websocket"
    $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSeconds))
    try {
        $sockJsSocket.ConnectAsync($sockJsUri, $cts.Token).GetAwaiter().GetResult()
    } finally {
        $cts.Dispose()
    }

    $openFrame = Receive-WebSocketText -Socket $sockJsSocket -Seconds $TimeoutSeconds
    if ($openFrame -ne "o") {
        throw "SockJS transport did not return the expected open frame. Actual: $openFrame"
    }

    $stompConnectFrame = "CONNECT`naccept-version:1.2`nhost:localhost`nheart-beat:0,0`n`n$([char]0)"
    $sockJsPayload = (ConvertTo-Json @($stompConnectFrame) -Compress)
    Send-WebSocketText -Socket $sockJsSocket -Text $sockJsPayload -Seconds $TimeoutSeconds

    $connectedFrame = Receive-WebSocketText -Socket $sockJsSocket -Seconds $TimeoutSeconds
    Assert-Contains -Text $connectedFrame -Expected "CONNECTED" -Message "SockJS/STOMP handshake did not reach CONNECTED."

    $disconnectFrame = "DISCONNECT`n`n$([char]0)"
    Send-WebSocketText -Socket $sockJsSocket -Text (ConvertTo-Json @($disconnectFrame) -Compress) -Seconds $TimeoutSeconds
} finally {
    Close-WebSocketQuietly -Socket $sockJsSocket
}

Write-Host "Checking raw gameplay websocket..."
$rawSocket = [System.Net.WebSockets.ClientWebSocket]::new()
try {
    $rawUri = [Uri]"$baseWsUrl/game/typing"
    $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSeconds))
    try {
        $rawSocket.ConnectAsync($rawUri, $cts.Token).GetAwaiter().GetResult()
    } finally {
        $cts.Dispose()
    }

    Send-WebSocketText -Socket $rawSocket -Text '{"action":"create"}' -Seconds $TimeoutSeconds
    $payload = Receive-WebSocketText -Socket $rawSocket -Seconds $TimeoutSeconds
    Assert-Contains -Text $payload -Expected '"gameState":"WAITING"' -Message "Raw websocket create flow did not return WAITING state."
    Assert-Contains -Text $payload -Expected '"id":"' -Message "Raw websocket create flow did not return a room id."
} finally {
    Close-WebSocketQuietly -Socket $rawSocket
}

Write-Host "Websocket smoke checks passed."
