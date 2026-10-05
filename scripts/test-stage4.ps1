<# Local-only stage 4 HTTP verification against the running project.
   Approved test records are retained; drafts/rejected records are soft-deleted.
   Base fixtures are deactivated unless -KeepFixtures. Never physically deletes history.
#>
param([string]$BaseUrl='http://127.0.0.1:5173',[switch]$KeepFixtures,[switch]$CreateBrowserFixtures)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
$script:passed=0; $script:requests=0; $script:contexts=@(); $script:rows=@(); $script:ids=@()
$tag=[guid]::NewGuid().ToString('N').Substring(0,8)
function Context {
    $h=[Net.Http.HttpClientHandler]::new();$h.CookieContainer=[Net.CookieContainer]::new()
    $c=[Net.Http.HttpClient]::new($h);$c.Timeout=[TimeSpan]::FromSeconds(30)
    $ctx=@{Client=$c;Csrf=$null;Header=$null};$script:contexts+=,$ctx;return $ctx
}
function Request($ctx,[string]$method,[string]$path,$body=$null,$file=$null,[switch]$Multipart,[switch]$NoCsrf,[switch]$NoRound) {
    $script:requests++
    $req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method),"$BaseUrl/api$path")
    if($method -eq 'POST' -and $path -match '^/admin/resource-reviews/\d+/(approve|reject)$' -and -not $NoRound){if($null -eq $body){$body=@{}};if(-not $body.ContainsKey('submissionNo')){$body.submissionNo=1}}
    if($method -notin @('GET','HEAD') -and -not $NoCsrf -and $ctx.Csrf){[void]$req.Headers.TryAddWithoutValidation($ctx.Header,$ctx.Csrf)}
    if($Multipart){
        $content=[Net.Http.MultipartFormDataContent]::new();$content.Add([Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json'),'metadata')
        if($file){$p=[Net.Http.ByteArrayContent]::new([byte[]]$file.Bytes);$p.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new($file.Mime);$content.Add($p,'file',$file.Name)}
        $req.Content=$content
    }elseif($null -ne $body){$req.Content=[Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json')}
    $resp=$null
    try{
        $resp=$ctx.Client.SendAsync($req).GetAwaiter().GetResult();$bytes=$resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $json=$null;try{$json=[Text.Encoding]::UTF8.GetString($bytes)|ConvertFrom-Json}catch{}
        return @{Status=[int]$resp.StatusCode;Json=$json;Bytes=$bytes;Disposition=[string]$resp.Content.Headers.ContentDisposition;Mime=[string]$resp.Content.Headers.ContentType;Cache=[string]$resp.Headers.CacheControl}
    }finally{if($resp){$resp.Dispose()};$req.Dispose()}
}
function Assert([string]$name,[bool]$ok){if(-not $ok){throw "FAIL: $name"};$script:passed++;Write-Host "PASS: $name"}
function Status([string]$name,$r,[int]$expected){Assert "$name HTTP $($r.Status)" ($r.Status -eq $expected);Assert "$name unified" ([bool]$r.Json.code -and [bool]$r.Json.message -and ($expected -ge 300 -or $r.Json.code -eq 'OK'))}
function Csrf($ctx){$r=Request $ctx GET '/auth/csrf';if($r.Status -ne 200){throw 'CSRF unavailable'};$ctx.Csrf=$r.Json.data.token;$ctx.Header=$r.Json.data.headerName}
function Login($name,$password){$ctx=Context;Csrf $ctx;Status "$name login" (Request $ctx POST '/auth/login' @{username=$name;password=$password}) 200;Csrf $ctx;return $ctx}
function PDF {
    $stream='BT /F1 16 Tf 30 100 Td (Stage 4 review material) Tj ET'
    $parts=@('%PDF-1.4',"1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj","2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj","3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>`nendobj","4 0 obj`n<< /Length $([Text.Encoding]::ASCII.GetByteCount($stream)) >>`nstream`n$stream`nendstream`nendobj","5 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>`nendobj")
    $text=$parts[0]+"`n";$offsets=@();foreach($p in $parts[1..5]){$offsets+=[Text.Encoding]::ASCII.GetByteCount($text);$text+=$p+"`n"}
    $xref=[Text.Encoding]::ASCII.GetByteCount($text);$text+="xref`n0 6`n0000000000 65535 f `n";foreach($o in $offsets){$text+=('{0:D10} 00000 n ' -f $o)+"`n"}
    $text+="trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n";return ,[Text.Encoding]::ASCII.GetBytes($text)
}
if($CreateBrowserFixtures){$d=Join-Path (Split-Path $PSScriptRoot -Parent) "tmp/stage4-browser-$tag";[void](New-Item -ItemType Directory -Path $d -Force);[IO.File]::WriteAllBytes((Join-Path $d 'review-material.pdf'),(PDF));Write-Output "BROWSER_FIXTURE=$d\review-material.pdf";return}
function BaseRow($type,$suffix){$p=@{name="S4-$tag-$suffix";description='第四阶段本地验证'};if($type -eq 'courses'){$p.courseCode="S4-$tag"};$r=Request $admin POST "/$type" $p;Status 'base fixture' $r 201;$script:rows+=@{Type=$type;Id=$r.Json.data.id};return $r.Json.data.id}
function Payload {return @{title="审核验证-$tag";description='审核案例简介';courseId=$course;categoryId=$category;elementIds=@($element)}}
function Create($payload){$r=Request $teacher POST '/teacher/resources' $payload $pdf -Multipart;Status 'create draft' $r 201;$script:ids+=,$r.Json.data.id;return $r.Json.data}
function Detail($id){$r=Request $teacher GET "/teacher/resources/$id";Status 'owner detail' $r 200;return $r.Json.data}
try{
    $admin=Login 'dev_admin' 'AdminDev#2026';$teacher=Login 'dev_teacher' 'TeacherDev#2026';$student=Login 'dev_student' 'StudentDev#2026';$teacherB=Login 'dev_teacher_b' 'TeacherBDev#2026';$anon=Context;Csrf $anon
    $course=BaseRow courses '课程';$category=BaseRow resource-categories '分类';$element=BaseRow ideological-elements '元素'
    $pdf=@{Name='review-material.pdf';Mime='application/pdf';Bytes=(PDF)}
    $draft=Create (Payload);$id=$draft.id
    Assert 'initial round zero' ($draft.submissionNo -eq 0 -and -not $draft.publishedAt)
    Status 'admin cannot see draft' (Request $admin GET "/admin/resource-reviews/$id") 404
    Status 'admin cannot read draft attachment' (Request $admin GET "/admin/resource-reviews/$id/attachment") 404
    Status 'DRAFT cannot approve' (Request $admin POST "/admin/resource-reviews/$id/approve") 409
    $p=Payload;$p.elementIds=@();$zero=Create $p
    Status 'zero elements submission' (Request $teacher POST "/teacher/resources/$($zero.id)/submit") 400
    Assert 'failed submit does not increment' ((Detail $zero.id).submissionNo -eq 0)
    foreach($row in $script:rows){
        Status 'deactivate reference' (Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='INACTIVE'}) 200
        Status "inactive $($row.Type) submit" (Request $teacher POST "/teacher/resources/$id/submit") 400
        Assert 'inactive submit retains draft and round' ((Detail $id).status -eq 'DRAFT' -and (Detail $id).submissionNo -eq 0)
        Status 'reactivate reference' (Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='ACTIVE'}) 200
    }
    foreach($actor in @(@{Name='teacher B';Ctx=$teacherB;Code=404},@{Name='student';Ctx=$student;Code=403},@{Name='admin';Ctx=$admin;Code=403})){
        Status "$($actor.Name) submit denied" (Request $actor.Ctx POST "/teacher/resources/$id/submit") $actor.Code
    }
    Status 'submit requires CSRF' (Request $teacher POST "/teacher/resources/$id/submit" -NoCsrf) 403
    $pending=Request $teacher POST "/teacher/resources/$id/submit";Status 'submit draft' $pending 200
    Assert 'pending round 1 and submit time' ($pending.Json.data.status -eq 'PENDING' -and $pending.Json.data.submissionNo -eq 1 -and $pending.Json.data.pendingSubmittedAt -and -not $pending.Json.data.publishedAt)
    foreach($actor in @(@{Name='teacher';Ctx=$teacher},@{Name='student';Ctx=$student})){
        foreach($path in @('/admin/resource-reviews',"/admin/resource-reviews/$id","/admin/resource-reviews/$id/attachment")){Status "$($actor.Name) review read denied" (Request $actor.Ctx GET $path) 403}
        Status "$($actor.Name) approve denied" (Request $actor.Ctx POST "/admin/resource-reviews/$id/approve") 403
        Status "$($actor.Name) reject denied" (Request $actor.Ctx POST "/admin/resource-reviews/$id/reject" @{reason='越权'}) 403
    }
    Status 'anonymous review list' (Request $anon GET '/admin/resource-reviews') 401
    Status 'anonymous review attachment' (Request $anon GET "/admin/resource-reviews/$id/attachment") 401
    Status 'anonymous decision with CSRF' (Request $anon POST "/admin/resource-reviews/$id/approve") 401
    foreach($op in @('approve','reject')){Status "decision $op needs CSRF" (Request $admin POST "/admin/resource-reviews/$id/$op" @{reason='原因'} -NoCsrf) 403}
    Status 'PENDING cannot edit' (Request $teacher PUT "/teacher/resources/$id" (Payload) $null -Multipart) 409
    Status 'PENDING cannot replace file' (Request $teacher PUT "/teacher/resources/$id" (Payload) $pdf -Multipart) 409
    Status 'PENDING cannot delete' (Request $teacher DELETE "/teacher/resources/$id") 409
    Status 'PENDING cannot resubmit' (Request $teacher POST "/teacher/resources/$id/submit") 409
    Status 'second teacher pending detail' (Request $teacherB GET "/teacher/resources/$id") 404
    Status 'second teacher pending edit' (Request $teacherB PUT "/teacher/resources/$id" (Payload) $null -Multipart) 404
    Status 'second teacher pending delete' (Request $teacherB DELETE "/teacher/resources/$id") 404
    Status 'admin cannot edit teacher body' (Request $admin PUT "/teacher/resources/$id" (Payload) $null -Multipart) 403
    $list=Request $admin GET "/admin/resource-reviews?keyword=$tag&courseId=$course&categoryId=$category&teacherId=$($draft.createdBy)&size=1";Status 'pending filtered list' $list 200
    Assert 'pending list only submitted owner fixture' ($list.Json.data.total -eq 1 -and $list.Json.data.items[0].id -eq $id -and $list.Json.data.items[0].teacherName)
    $attachment=Request $admin GET "/admin/resource-reviews/$id/attachment";Assert 'protected attachment readable' ($attachment.Status -eq 200 -and $attachment.Mime -eq 'application/pdf' -and [Convert]::ToBase64String($attachment.Bytes) -eq [Convert]::ToBase64String($pdf.Bytes));Assert 'inline no-store headers' ($attachment.Disposition.StartsWith('inline') -and $attachment.Cache -eq 'no-store')
    $attachment=Request $admin GET "/admin/resource-reviews/$id/attachment?download=true";Assert 'protected attachment download' ($attachment.Status -eq 200 -and $attachment.Disposition.StartsWith('attachment'))
    foreach($reason in @('',' ',"`t`n",('x'*1001))){Status 'invalid reject reason' (Request $admin POST "/admin/resource-reviews/$id/reject" @{reason=$reason}) 400}
    Status 'missing decision round' (Request $admin POST "/admin/resource-reviews/$id/approve" @{} -NoRound) 400
    Status 'invalid decision round' (Request $admin POST "/admin/resource-reviews/$id/approve" @{submissionNo=0}) 400
    Status 'missing reject reason' (Request $admin POST "/admin/resource-reviews/$id/reject" @{submissionNo=1}) 400
    $r=Request $admin POST "/admin/resource-reviews/$id/approve";Status 'normal approve' $r 200
    Assert 'approved round published history' ($r.Json.data.status -eq 'APPROVED' -and $r.Json.data.submissionNo -eq 1 -and $r.Json.data.publishedAt -and @($r.Json.data.auditRecords).Count -eq 1 -and $r.Json.data.auditRecords[0].decision -eq 'APPROVE')
    foreach($op in @('approve','reject')){Status "APPROVED $op denied" (Request $admin POST "/admin/resource-reviews/$id/$op" @{reason='重复'}) 409}
    Status 'APPROVED teacher edit' (Request $teacher PUT "/teacher/resources/$id" (Payload) $null -Multipart) 409
    Status 'APPROVED teacher delete' (Request $teacher DELETE "/teacher/resources/$id") 409
    Status 'APPROVED teacher submit' (Request $teacher POST "/teacher/resources/$id/submit") 409
    Status 'approved admin history' (Request $admin GET "/admin/resource-reviews/$id") 200
    $rework=Create (Payload);$rid=$rework.id
    Status 'rework first submit' (Request $teacher POST "/teacher/resources/$rid/submit") 200
    $r=Request $admin POST "/admin/resource-reviews/$rid/reject" @{reason=' 请补充课程思政案例说明 '};Status 'first rejection' $r 200
    Assert 'rejected first history and reason' ($r.Json.data.status -eq 'REJECTED' -and $r.Json.data.submissionNo -eq 1 -and -not $r.Json.data.publishedAt -and $r.Json.data.auditRecords[0].reason -eq '请补充课程思政案例说明')
    Status 'rejected admin detail' (Request $admin GET "/admin/resource-reviews/$rid") 200
    foreach($op in @('approve','reject')){Status "REJECTED $op denied" (Request $admin POST "/admin/resource-reviews/$rid/$op" @{reason='再次'}) 409}
    $p=Payload;$p.title="审核验证-$tag-已修改";$p.description='已补充案例说明'
    $r=Request $teacher PUT "/teacher/resources/$rid" $p $pdf -Multipart;Status 'edit rejected and replace file' $r 200
    Assert 'edit retains rejected round and history' ($r.Json.data.status -eq 'REJECTED' -and $r.Json.data.submissionNo -eq 1 -and @($r.Json.data.auditRecords).Count -eq 1)
    $r=Request $teacher POST "/teacher/resources/$rid/submit";Status 'resubmit rejected' $r 200;Assert 'second round pending' ($r.Json.data.status -eq 'PENDING' -and $r.Json.data.submissionNo -eq 2)
    Status 'stale first round cannot approve second submission' (Request $admin POST "/admin/resource-reviews/$rid/approve" @{submissionNo=1}) 409
    $r=Request $admin POST "/admin/resource-reviews/$rid/approve" @{submissionNo=2};Status 'second round approve' $r 200
    Assert 'two immutable audit records' ($r.Json.data.status -eq 'APPROVED' -and $r.Json.data.submissionNo -eq 2 -and $r.Json.data.publishedAt -and @($r.Json.data.auditRecords).Count -eq 2 -and $r.Json.data.auditRecords[0].decision -eq 'REJECT' -and $r.Json.data.auditRecords[1].decision -eq 'APPROVE')
    $last=Detail $rid;Assert 'teacher sees both histories' (@($last.auditRecords).Count -eq 2)
    foreach($query in @('status=DRAFT','status=PENDING_REVIEW','page=0','size=101','teacherId=0','courseId=-1','categoryId=0')){Status "invalid review filter $query" (Request $admin GET "/admin/resource-reviews?$query") 400}
    $l=Request $teacher GET "/teacher/resources?keyword=$tag&status=APPROVED&size=1";Status 'teacher approved page' $l 200;Assert 'two approved fixtures paginated' ($l.Json.data.total -eq 2 -and @($l.Json.data.items).Count -eq 1)
    $next=Request $teacher GET "/teacher/resources?keyword=$tag&status=APPROVED&size=1&page=2";Status 'teacher approved second page' $next 200;Assert 'different pages' ($next.Json.data.items[0].id -ne $l.Json.data.items[0].id)
    $l=Request $admin GET "/admin/resource-reviews?keyword=$tag&status=APPROVED&size=1";Status 'admin approved page' $l 200;Assert 'admin total' ($l.Json.data.total -eq 2)
    $l=Request $admin GET "/admin/resource-reviews?keyword=$tag";Status 'default pending page' $l 200;Assert 'approved absent default list' ($l.Json.data.total -eq 0)
    $fake=Payload;$fake.status='APPROVED';Status 'ordinary PUT forged status' (Request $teacher PUT "/teacher/resources/$($zero.id)" $fake $null -Multipart) 400
    Write-Host "TOTAL_ASSERTIONS_PASSED=$script:passed HTTP_REQUESTS=$script:requests TEST_TAG=$tag APPROVED_IDS=$id,$rid"
    Write-Host "BROWSER_BASE course=$course category=$category element=$element KEEP=$KeepFixtures"
}finally{
    $checks=$script:requests
    foreach($id in $script:ids){try{$r=Request $teacher GET "/teacher/resources/$id";if($r.Json.data.status -in @('DRAFT','REJECTED')){[void](Request $teacher DELETE "/teacher/resources/$id")}}catch{Write-Warning "Test record retained: $id"}}
    if(-not $KeepFixtures){foreach($row in $script:rows){try{[void](Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='INACTIVE'})}catch{Write-Warning 'Base fixture cleanup failed'}}}
    Write-Host "CLEANUP_HTTP_REQUESTS=$($script:requests-$checks)";foreach($ctx in $script:contexts){$ctx.Client.Dispose()}
}
