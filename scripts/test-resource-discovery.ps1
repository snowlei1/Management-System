<# Read-only discovery regression against the local development database. #>
param([string]$BaseUrl='http://127.0.0.1:5173')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
$checks=0
function Check($name,$condition){if(-not $condition){throw "FAIL: $name"};$script:checks++;Write-Host "PASS: $name"}
function Context { $handler=[Net.Http.HttpClientHandler]::new();$handler.CookieContainer=[Net.CookieContainer]::new();return @{Client=[Net.Http.HttpClient]::new($handler);Handler=$handler;Token=$null;Header=$null} }
function Request($ctx,$method,$path,$body=$null){
    $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method),"$BaseUrl/api$path")
    if($ctx.Token){[void]$request.Headers.TryAddWithoutValidation($ctx.Header,$ctx.Token)}
    if($null -ne $body){$request.Content=[Net.Http.StringContent]::new(($body|ConvertTo-Json),[Text.Encoding]::UTF8,'application/json')}
    $response=$ctx.Client.SendAsync($request).GetAwaiter().GetResult()
    try{$json=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json;return @{Status=[int]$response.StatusCode;Data=$json.data}}
    finally{$response.Dispose();$request.Dispose()}
}
function Login($name,$password){$ctx=Context;$csrf=Request $ctx GET '/auth/csrf';$ctx.Token=$csrf.Data.token;$ctx.Header=$csrf.Data.headerName;Check "$name login" ((Request $ctx POST '/auth/login' @{username=$name;password=$password}).Status -eq 200);return $ctx}
$student=$null;$teacher=$null;$admin=$null;$anon=Context
try {
    $student=Login 'dev_student' 'StudentDev#2026';$teacher=Login 'dev_teacher' 'TeacherDev#2026';$admin=Login 'dev_admin' 'AdminDev#2026'
    foreach($ctx in @($student,$teacher)){
        $all=Request $ctx GET '/resources?size=100';Check 'list available' ($all.Status -eq 200 -and $all.Data.total -gt 0)
        foreach($format in @('PDF','IMAGE','OFFICE')){
            $result=Request $ctx GET "/resources?format=$format&size=100";Check "$format filter HTTP" ($result.Status -eq 200)
            foreach($item in $result.Data.items){$correct=switch($format){PDF{$item.fileMimeType -eq 'application/pdf'} IMAGE{$item.fileMimeType -in @('image/png','image/jpeg')} OFFICE{$item.fileMimeType -notin @('application/pdf','image/png','image/jpeg')}};Check "$format matches actual MIME" $correct}
        }
        $result=Request $ctx GET '/resources?sort=OLDEST&size=100';Check 'oldest order HTTP' ($result.Status -eq 200)
        $last=[datetime]::MinValue;foreach($item in $result.Data.items){$date=[datetime]$item.publishedAt;Check 'oldest ordering' ($date -ge $last);$last=$date}
        Check 'title order accepted' ((Request $ctx GET '/resources?sort=TITLE').Status -eq 200)
        $result=Request $ctx GET '/resources?from=2026-10-20&to=2026-10-20&size=100';Check 'inclusive date range' ($result.Status -eq 200 -and @($result.Data.items|Where-Object {([datetime]$_.publishedAt).Date -ne [datetime]'2026-10-20'}).Count -eq 0)
        $result=Request $ctx GET '/favorites?keyword=__no_such_resource__&format=PDF';Check 'favorites filters apply' ($result.Status -eq 200 -and $result.Data.total -eq 0)
        foreach($path in @('/resources?sort=DROP','/resources?format=VIDEO','/resources?from=2026-02-30','/resources?from=2026-10-20&to=2026-10-01','/favorites?page=0')){Check 'invalid options rejected' ((Request $ctx GET $path).Status -eq 400)}
    }
    Check 'admin resource boundary' ((Request $admin GET '/resources?format=PDF').Status -eq 403)
    Check 'anonymous resource boundary' ((Request $anon GET '/resources?format=PDF').Status -eq 401)
    Write-Host "DISCOVERY_CHECKS_PASSED=$checks"
} finally {foreach($ctx in @($student,$teacher,$admin,$anon)){if($ctx){$ctx.Client.Dispose()}}}
