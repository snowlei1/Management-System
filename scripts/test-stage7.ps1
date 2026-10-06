<# Stage 7 local development regression. New endpoints are read-only. Keeps approved fixtures and audit history. #>
param([string]$BaseUrl='http://127.0.0.1:5173')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
$script:contexts=@();$script:passed=0;$script:requests=0;$script:rows=@();$draftId=0
$tag=[guid]::NewGuid().ToString('N').Substring(0,8)
$fixturePath=Join-Path (Split-Path $PSScriptRoot -Parent) 'tmp/stage6/课程思政联调案例.pdf'
if(-not (Test-Path -LiteralPath $fixturePath)) {
    & (Join-Path $PSScriptRoot 'test-stage6.ps1') -CreateBrowserFixture
}
function Context {
    $h=[Net.Http.HttpClientHandler]::new();$h.CookieContainer=[Net.CookieContainer]::new();$c=[Net.Http.HttpClient]::new($h);$c.Timeout=[TimeSpan]::FromSeconds(30)
    $ctx=@{Client=$c;Handler=$h;Csrf=$null;Header=$null};$script:contexts+=,$ctx;return $ctx
}
function Request($ctx,[string]$method,[string]$path,$body=$null,$file=$null,[switch]$Multipart,[switch]$NoCsrf) {
    $script:requests++;$req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method),"$BaseUrl/api$path")
    if($method -notin @('GET','HEAD') -and -not $NoCsrf -and $ctx.Csrf){[void]$req.Headers.TryAddWithoutValidation($ctx.Header,$ctx.Csrf)}
    if($Multipart){$content=[Net.Http.MultipartFormDataContent]::new();$content.Add([Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json'),'metadata');if($file){$part=[Net.Http.ByteArrayContent]::new([byte[]]$file.Bytes);$part.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new($file.Mime);$content.Add($part,'file',$file.Name)};$req.Content=$content}
    elseif($null -ne $body){$req.Content=[Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json')}
    $resp=$null
    try{$resp=$ctx.Client.SendAsync($req).GetAwaiter().GetResult();$bytes=$resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult();$json=$null;try{$json=[Text.Encoding]::UTF8.GetString($bytes)|ConvertFrom-Json}catch{};return @{Status=[int]$resp.StatusCode;Json=$json;Bytes=$bytes}}
    finally{if($resp){$resp.Dispose()};$req.Dispose()}
}
function Assert([string]$name,[bool]$ok){if(-not $ok){throw "FAIL: $name"};$script:passed++;Write-Host "PASS: $name"}
function Status([string]$name,$r,[int]$expected){Assert "$name HTTP $($r.Status)" ($r.Status -eq $expected);Assert "$name unified" ([bool]$r.Json.code -and [bool]$r.Json.message -and ($expected -ge 300 -or $r.Json.code -eq 'OK'))}
function Csrf($ctx){$r=Request $ctx GET '/auth/csrf';if($r.Status -ne 200){throw 'CSRF unavailable'};$ctx.Csrf=$r.Json.data.token;$ctx.Header=$r.Json.data.headerName}
function Login($name,$password){$ctx=Context;Csrf $ctx;Status "$name login" (Request $ctx POST '/auth/login' @{username=$name;password=$password}) 200;Csrf $ctx;return $ctx}
function BaseRow($type,$suffix){$p=@{name="S7-$tag-$suffix";description='第七阶段只读展示联调夹具'};if($type -eq 'courses'){$p.courseCode="S7-$tag"};$r=Request $admin POST "/$type" $p;Status 'base create' $r 201;$script:rows+=@{Type=$type;Id=$r.Json.data.id};return [long]$r.Json.data.id}
function Usage {return (Request $admin GET '/admin/statistics/overview').Json.data.usage}
try {
    $admin=Login 'dev_admin' 'AdminDev#2026';$teacher=Login 'dev_teacher' 'TeacherDev#2026';$student=Login 'dev_student' 'StudentDev#2026';$other=Login 'dev_teacher_b' 'TeacherBDev#2026';$anon=Context
    $course=BaseRow courses '课程';$category=BaseRow resource-categories '分类';$element=BaseRow ideological-elements '元素'
    $file=@{Name='课程思政联调案例.pdf';Mime='application/pdf';Bytes=[IO.File]::ReadAllBytes($fixturePath)}
    $p=@{title="S7-$tag-资源";description='真实联调：固定课程与思政关联';courseId=$course;categoryId=$category;elementIds=@($element)}
    $r=Request $teacher POST '/teacher/resources' $p $file -Multipart;Status 'draft create' $r 201;$id=[long]$r.Json.data.id
    Status 'draft presentation invisible' (Request $student GET "/portal/resources/$id") 404
    Status 'draft admin invisible' (Request $admin GET "/admin/resource-reviews/$id") 404
    Status 'submit missing CSRF' (Request $teacher POST "/teacher/resources/$id/submit" -NoCsrf) 403
    Status 'submit round1' (Request $teacher POST "/teacher/resources/$id/submit") 200
    $pending=Request $admin GET '/admin/review-overview';Status 'admin recent pending' $pending 200;Assert 'newest pending first' ($pending.Json.data[0].resource.id -eq $id)
    Status 'reject' (Request $admin POST "/admin/resource-reviews/$id/reject" @{submissionNo=1;reason='第七阶段测试：补充课程关联说明'}) 200
    $own=Request $teacher GET "/teacher/resource-presentations?ids=$id";Status 'teacher own presentations' $own 200;Assert 'real audit reason' ($own.Json.data[0].latestAudit.reason -eq '第七阶段测试：补充课程关联说明')
    Status 'other teacher IDOR' (Request $other GET "/teacher/resource-presentations?ids=$id") 404
    Status 'edit rejected' (Request $teacher PUT "/teacher/resources/$id" $p $null -Multipart) 200
    Status 'resubmit' (Request $teacher POST "/teacher/resources/$id/submit") 200
    Status 'approve stale round' (Request $admin POST "/admin/resource-reviews/$id/approve" @{submissionNo=1}) 409
    Status 'approve' (Request $admin POST "/admin/resource-reviews/$id/approve" @{submissionNo=2}) 200
    $r=Request $teacher POST '/teacher/resources' $p $file -Multipart;Status 'related draft' $r 201;$relatedId=[long]$r.Json.data.id
    Status 'related submit' (Request $teacher POST "/teacher/resources/$relatedId/submit") 200;Status 'related approve' (Request $admin POST "/admin/resource-reviews/$relatedId/approve" @{submissionNo=1}) 200
    $p.elementIds=@();$r=Request $teacher POST '/teacher/resources' $p $file -Multipart;Status 'zero tag draft allowed' $r 201;$draftId=[long]$r.Json.data.id;Status 'zero tags submit denied' (Request $teacher POST "/teacher/resources/$draftId/submit") 400
    $consumerPaths=@('/portal/courses',"/portal/courses/$course",'/portal/ideological-topics',"/portal/ideological-topics/$element", "/portal/resources/$id",'/history/browse','/history/downloads')
    $adminPaths=@('/admin/published-resources','/admin/review-overview')
    $teacherPaths=@('/teacher/resource-dashboard',"/teacher/resource-presentations?ids=$id")
    $before=Usage
    foreach($path in ($consumerPaths+$adminPaths+$teacherPaths)){Status 'anonymous read' (Request $anon GET $path) 401}
    foreach($ctx in @($teacher,$student)){foreach($path in $consumerPaths){Status 'consumer read' (Request $ctx GET $path) 200};foreach($path in $adminPaths){Status 'nonadmin read denied' (Request $ctx GET $path) 403}}
    foreach($path in $consumerPaths){Status 'admin consumer boundary' (Request $admin GET $path) 403}
    foreach($path in $teacherPaths){Status 'student teacher boundary' (Request $student GET $path) 403;Status 'admin teacher boundary' (Request $admin GET $path) 403}
    $courseView=Request $student GET "/portal/courses/$course";Assert 'course count2 related element' ($courseView.Json.data.item.resourceCount -eq 2 -and $courseView.Json.data.related[0].id -eq $element)
    $topicView=Request $student GET "/portal/ideological-topics/$element";Assert 'topic count2 related course' ($topicView.Json.data.item.resourceCount -eq 2 -and $topicView.Json.data.related[0].id -eq $course)
    $detail=Request $student GET "/portal/resources/$id";Assert 'fixed relations omit self' (@($detail.Json.data.sameCourse).Count -eq 1 -and $detail.Json.data.sameCourse[0].id -eq $relatedId -and $detail.Json.data.sameElements[0].id -eq $relatedId)
    $ledger=Request $admin GET "/admin/published-resources?keyword=$tag&courseId=$course&categoryId=$category&teacherId=2&page=1&size=1";Status 'ledger filtered page' $ledger 200;Assert 'ledger total2 size1' ($ledger.Json.data.total -eq 2 -and @($ledger.Json.data.items).Count -eq 1)
    $dash=Request $teacher GET '/teacher/resource-dashboard';Status 'teacher dashboard' $dash 200;Assert 'dashboard current user only' (@($dash.Json.data.recent|Where-Object {$_.resource.createdBy -ne 2}).Count -eq 0)
    foreach($path in @('/history/wrong','/history/browse?page=0','/history/downloads?size=101','/portal/courses/0','/portal/resources/-1','/teacher/resource-presentations?ids=0')){Status 'invalid read params' (Request $teacher GET $path) 400}
    Status 'invalid ledger page' (Request $admin GET '/admin/published-resources?page=0') 400
    Status 'invalid ledger id' (Request $admin GET '/admin/published-resources?teacherId=-1') 400
    $after=Usage;Assert 'new read endpoints do not record usage' ($after.browseEvents -eq $before.browseEvents -and $after.downloadRequests -eq $before.downloadRequests -and $after.currentFavorites -eq $before.currentFavorites)
    Status 'public detail' (Request $student GET "/resources/$id") 200;Status 'public refresh' (Request $student GET "/resources/$id") 200
    Assert 'preview bytes' ((Request $student GET "/resources/$id/preview").Status -eq 200)
    Status 'favorite' (Request $student POST "/resources/$id/favorite") 200
    Assert 'download bytes' ((Request $student GET "/resources/$id/download").Status -eq 200)
    $presentation=Request $student GET "/portal/resources/$id";Assert 'usage precise deltas' ($presentation.Json.data.usage.browseEvents -eq 2 -and $presentation.Json.data.usage.downloadRequests -eq 1 -and $presentation.Json.data.usage.currentFavorites -eq 1)
    $history=Request $student GET '/history/browse?userId=2';Assert 'history repeated event scoped to Session' (@($history.Json.data.items|Where-Object {$_.resourceId -eq $id}).Count -eq 2)
    $otherHistory=Request $other GET '/history/browse?userId=3';Assert 'cannot spoof history identity' (@($otherHistory.Json.data.items|Where-Object {$_.resourceId -eq $id}).Count -eq 0)
    $downloads=Request $student GET '/history/downloads';Assert 'download request history' ($downloads.Json.data.items[0].resourceId -eq $id)
    $privates=($presentation.Json.data|ConvertTo-Json -Depth 20);Assert 'public DTO no file key or hash' (-not $privates.Contains('storageKey') -and -not $privates.Contains('password'))
    Status 'logout' (Request $student POST '/auth/logout') 200;Status 'history after logout' (Request $student GET '/history/browse') 401
    Write-Host "TOTAL_ASSERTIONS_PASSED=$script:passed HTTP_REQUESTS=$script:requests TEST_TAG=$tag RESOURCE_IDS=$id,$relatedId"
} finally {
    $checks=$script:requests
    if($draftId){try{[void](Request $teacher DELETE "/teacher/resources/$draftId")}catch{Write-Warning 'Draft fixture retained'}}
    foreach($row in $script:rows){try{[void](Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='INACTIVE'})}catch{Write-Warning 'Base fixture retained'}}
    Write-Host "CLEANUP_HTTP_REQUESTS=$($script:requests-$checks)";foreach($ctx in $script:contexts){$ctx.Client.Dispose()}
}
