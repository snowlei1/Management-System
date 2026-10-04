<# Local-development integration checks. Requires initialized dev_* accounts and running servers.
   Writes uniquely named test rows and leaves them INACTIVE; never physically deletes data.
   Password defaults refer only to the public local-development fixtures in README.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:5173',
    [string]$AdminPassword = $env:STAGE2_ADMIN_PASSWORD,
    [string]$TeacherPassword = $env:STAGE2_TEACHER_PASSWORD,
    [string]$StudentPassword = $env:STAGE2_STUDENT_PASSWORD
)
$ErrorActionPreference = 'Stop'
if (-not $AdminPassword) { $AdminPassword = 'AdminDev#2026' }
if (-not $TeacherPassword) { $TeacherPassword = 'TeacherDev#2026' }
if (-not $StudentPassword) { $StudentPassword = 'StudentDev#2026' }
$script:passed = 0
$script:createdRows = @()
$tag = [guid]::NewGuid().ToString('N').Substring(0, 8)

function New-Context {
    return @{ Session = [Microsoft.PowerShell.Commands.WebRequestSession]::new(); Csrf = $null; Header = $null }
}
function Invoke-Api($Context, [string]$Method, [string]$Path, $Body = $null, [switch]$NoCsrf) {
    # Invoke-WebRequest can persist request headers in WebRequestSession; a NoCsrf
    # assertion must not inherit the header from an earlier authenticated write.
    $Context.Session.Headers.Clear()
    $headers = @{ Accept = 'application/json' }
    if ($Method -notin @('GET', 'HEAD') -and -not $NoCsrf -and $Context.Csrf) {
        $headers[$Context.Header] = $Context.Csrf
    }
    $args = @{ Uri = "$BaseUrl/api$Path"; Method = $Method; WebSession = $Context.Session;
        Headers = $headers; UseBasicParsing = $true; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $args.ContentType = 'application/json; charset=utf-8'
        $args.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress))
    }
    try {
        $response = Invoke-WebRequest @args
        return @{ Status = [int]$response.StatusCode; Json = ($response.Content | ConvertFrom-Json) }
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        $text = $_.ErrorDetails.Message
        if (-not $text -and $_.Exception.Response -is [System.Net.HttpWebResponse]) {
            $reader = [IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
        $json = $null
        if ($text) { try { $json = $text | ConvertFrom-Json } catch { } }
        return @{ Status = $status; Json = $json }
    }
}
function Assert-True([string]$Name, [bool]$Condition) {
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:passed++
    Write-Output "PASS: $Name"
}
function Assert-Status([string]$Name, $Result, [int]$Expected) {
    Assert-True "$Name (expected HTTP $Expected, actual $($Result.Status))" ($Result.Status -eq $Expected)
    if ($Expected -lt 300) { Assert-True "$Name unified response" ($Result.Json.code -eq 'OK') }
    else { Assert-True "$Name unified error" ([bool]$Result.Json.code -and [bool]$Result.Json.message) }
}
function Refresh-Csrf($Context) {
    $result = Invoke-Api $Context GET '/auth/csrf'
    if ($result.Status -ne 200) { throw 'Unable to obtain CSRF token' }
    $Context.Csrf = $result.Json.data.token
    $Context.Header = $result.Json.data.headerName
}
function Login([string]$Username, [string]$Password) {
    $context = New-Context
    Refresh-Csrf $context
    $result = Invoke-Api $context POST '/auth/login' @{ username = $Username; password = $Password }
    Assert-Status "$Username login regression" $result 200
    Refresh-Csrf $context
    return $context
}

# Assertions emit text; isolate it from the returned session object.
try {
$adminResults = @(Login 'dev_admin' $AdminPassword)
$adminResults[0..($adminResults.Count - 2)] | Write-Output
$admin = $adminResults[-1]
$teacherResults = @(Login 'dev_teacher' $TeacherPassword)
$teacherResults[0..($teacherResults.Count - 2)] | Write-Output
$teacher = $teacherResults[-1]
$studentResults = @(Login 'dev_student' $StudentPassword)
$studentResults[0..($studentResults.Count - 2)] | Write-Output
$student = $studentResults[-1]
$anonymous = New-Context
Refresh-Csrf $anonymous
Assert-Status 'administrator user list regression' (Invoke-Api $admin GET '/users') 200
Assert-Status 'teacher user-management denial regression' (Invoke-Api $teacher GET '/users') 403
Assert-Status 'student user-management denial regression' (Invoke-Api $student GET '/users') 403

foreach ($type in @('courses', 'ideological-elements', 'resource-categories')) {
    $isCourse = $type -eq 'courses'
    $name = "阶段二接口-$type-$tag"
    $payload = @{ name = " $name "; description = '接口测试说明' }
    if ($isCourse) { $payload.courseCode = " S2-$tag " }
    Assert-Status "$type admin list" (Invoke-Api $admin GET "/$type") 200
    $created = Invoke-Api $admin POST "/$type" $payload
    Assert-Status "$type admin create" $created 201
    $id = $created.Json.data.id
    $script:createdRows += @{ Type = $type; Id = $id }
    Assert-True "$type trim and description persisted" ($created.Json.data.name -eq $name -and $created.Json.data.description -eq '接口测试说明')
    $payload.name = "$name-修改"
    if ($isCourse) { $payload.courseCode = "S2-$tag" }
    $updated = Invoke-Api $admin PUT "/$type/$id" $payload
    Assert-Status "$type admin edit" $updated 200
    Assert-True "$type edit persisted" ($updated.Json.data.name -eq $payload.name)
    $search = Invoke-Api $admin GET "/${type}?keyword=$([uri]::EscapeDataString($tag))&page=1&size=1"
    Assert-Status "$type search and pagination" $search 200
    Assert-True "$type pagination total" ($search.Json.data.total -eq 1 -and @($search.Json.data.items).Count -eq 1)
    Assert-Status "$type duplicate create" (Invoke-Api $admin POST "/$type" $payload) 409
    $second = @{ name = "$name-另一条"; description = $null }
    if ($isCourse) { $second.courseCode = "S2B-$tag" }
    $other = Invoke-Api $admin POST "/$type" $second
    Assert-Status "$type second row for duplicate edit" $other 201
    $otherId = $other.Json.data.id
    $script:createdRows += @{ Type = $type; Id = $otherId }
    $secondPage = Invoke-Api $admin GET "/${type}?keyword=$([uri]::EscapeDataString($tag))&page=2&size=1"
    Assert-Status "$type second page" $secondPage 200
    Assert-True "$type second page count" ($secondPage.Json.data.total -eq 2 -and @($secondPage.Json.data.items).Count -eq 1)
    Assert-True "$type second page stable order" ($secondPage.Json.data.items[0].id -eq $id)
    Assert-Status "$type duplicate edit" (Invoke-Api $admin PUT "/$type/$otherId" $payload) 409
    Assert-Status "$type blank name" (Invoke-Api $admin POST "/$type" @{ name = ' '; courseCode = 'VALID' }) 400
    Assert-Status "$type missing name" (Invoke-Api $admin POST "/$type" @{}) 400
    $limit = if ($isCourse) { 120 } elseif ($type -eq 'ideological-elements') { 100 } else { 80 }
    Assert-Status "$type overlong name" (Invoke-Api $admin POST "/$type" @{ name = ('名' * ($limit + 1)); courseCode = 'VALID' }) 400
    Assert-Status "$type overlong description" (Invoke-Api $admin POST "/$type" @{ name = '名称'; courseCode = 'VALID'; description = ('文' * 1001) }) 400
    Assert-Status "$type invalid status" (Invoke-Api $admin PATCH "/$type/$id/status" @{ status = 'DISABLED' }) 400
    Assert-Status "$type empty status" (Invoke-Api $admin PATCH "/$type/$id/status" @{ status = '' }) 400
    Assert-Status "$type invalid status filter" (Invoke-Api $admin GET "/${type}?status=UNKNOWN") 400
    Assert-Status "$type invalid page" (Invoke-Api $admin GET "/${type}?page=0") 400
    Assert-Status "$type invalid page size" (Invoke-Api $admin GET "/${type}?size=101") 400
    Assert-Status "$type nonexistent row" (Invoke-Api $admin PUT "/$type/9223372036854775807" $payload) 404
    Assert-Status "$type malformed id" (Invoke-Api $admin PATCH "/$type/not-a-number/status" @{ status = 'INACTIVE' }) 400
    Assert-Status "$type missing CSRF" (Invoke-Api $admin POST "/$type" $second -NoCsrf) 403
    Assert-Status "$type anonymous list" (Invoke-Api $anonymous GET "/$type") 401
    Assert-Status "$type anonymous options" (Invoke-Api $anonymous GET "/options/$type") 401
    Assert-Status "$type anonymous write with CSRF" (Invoke-Api $anonymous POST "/$type" $second) 401
    foreach ($actor in @(@{ Name = 'teacher'; Context = $teacher }, @{ Name = 'student'; Context = $student })) {
        Assert-Status "$type $($actor.Name) management list denied" (Invoke-Api $actor.Context GET "/$type") 403
        Assert-Status "$type $($actor.Name) create denied" (Invoke-Api $actor.Context POST "/$type" $second) 403
        Assert-Status "$type $($actor.Name) edit denied" (Invoke-Api $actor.Context PUT "/$type/$id" $payload) 403
        Assert-Status "$type $($actor.Name) status denied" (Invoke-Api $actor.Context PATCH "/$type/$id/status" @{ status = 'INACTIVE' }) 403
        $options = Invoke-Api $actor.Context GET "/options/$type"
        Assert-Status "$type $($actor.Name) active options" $options 200
        Assert-True "$type $($actor.Name) sees enabled test row" ($id -in @($options.Json.data | ForEach-Object { $_.id }))
    }
    $disabled = Invoke-Api $admin PATCH "/$type/$id/status" @{ status = 'INACTIVE' }
    Assert-Status "$type admin disable" $disabled 200
    Assert-True "$type disable preserves identity and details" ($disabled.Json.data.id -eq $id -and $disabled.Json.data.name -eq $payload.name -and $disabled.Json.data.status -eq 'INACTIVE')
    Assert-Status "$type inactive uniqueness retained" (Invoke-Api $admin POST "/$type" $payload) 409
    foreach ($actor in @(@{ Name = 'teacher'; Context = $teacher }, @{ Name = 'student'; Context = $student })) {
        $options = Invoke-Api $actor.Context GET "/options/${type}?status=INACTIVE"
        Assert-Status "$type $($actor.Name) options after disable" $options 200
        Assert-True "$type $($actor.Name) cannot expand options to inactive rows" ($id -notin @($options.Json.data | ForEach-Object { $_.id }))
    }
    $inactiveList = Invoke-Api $admin GET "/${type}?keyword=$([uri]::EscapeDataString($tag))&status=INACTIVE"
    Assert-True "$type inactive row retained for admin" ($id -in @($inactiveList.Json.data.items | ForEach-Object { $_.id }))
    Assert-Status "$type admin reenable" (Invoke-Api $admin PATCH "/$type/$id/status" @{ status = 'ACTIVE' }) 200
    Assert-True "$type reenabled row returns to options" ($id -in @((Invoke-Api $teacher GET "/options/$type").Json.data | ForEach-Object { $_.id }))
    Assert-Status "$type physical DELETE unsupported" (Invoke-Api $admin DELETE "/$type/$id") 405
    Assert-Status "$type final test row deactivation" (Invoke-Api $admin PATCH "/$type/$id/status" @{ status = 'INACTIVE' }) 200
    Assert-Status "$type final second row deactivation" (Invoke-Api $admin PATCH "/$type/$otherId/status" @{ status = 'INACTIVE' }) 200
}
Assert-Status 'course missing code' (Invoke-Api $admin POST '/courses' @{ name = '课程名称' }) 400
Assert-Status 'course blank code' (Invoke-Api $admin POST '/courses' @{ name = '课程名称'; courseCode = ' ' }) 400
Assert-Status 'course overlong code' (Invoke-Api $admin POST '/courses' @{ name = '课程名称'; courseCode = ('C' * 41) }) 400
Write-Output "TOTAL_ASSERTIONS_PASSED=$script:passed TEST_TAG=$tag"
} finally {
    foreach ($row in $script:createdRows) {
        try {
            $cleanup = Invoke-Api $admin PATCH "/$($row.Type)/$($row.Id)/status" @{ status = 'INACTIVE' }
            if ($cleanup.Status -ne 200) { Write-Warning "Test row cleanup failed: $($row.Type)/$($row.Id)" }
        } catch { Write-Warning "Test row cleanup request failed: $($row.Type)/$($row.Id)" }
    }
}
