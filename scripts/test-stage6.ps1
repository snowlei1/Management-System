<# Local development only: complete review/use/statistics chain, independent SQL comparison.
   Keeps the approved resource and audit/usage history; does not add a delete/downlisting API. #>
param([string]$BaseUrl='http://127.0.0.1:5173',[switch]$CreateBrowserFixture)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
$project=Split-Path $PSScriptRoot -Parent
$script:requests=0;$script:passed=0;$script:contexts=@();$script:rows=@();$resourceId=0;$zeroId=0
$tag=[guid]::NewGuid().ToString('N').Substring(0,8)
function PDF {
    $stream='BT /F1 16 Tf 30 100 Td (Stage 6 final integration fixture) Tj ET'
    $parts=@('%PDF-1.4',"1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj","2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj","3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 360 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>`nendobj","4 0 obj`n<< /Length $([Text.Encoding]::ASCII.GetByteCount($stream)) >>`nstream`n$stream`nendstream`nendobj","5 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>`nendobj")
    $text=$parts[0]+"`n";$offsets=@();foreach($p in $parts[1..5]){$offsets+=[Text.Encoding]::ASCII.GetByteCount($text);$text+=$p+"`n"}
    $xref=[Text.Encoding]::ASCII.GetByteCount($text);$text+="xref`n0 6`n0000000000 65535 f `n";foreach($o in $offsets){$text+=('{0:D10} 00000 n ' -f $o)+"`n"}
    $text+="trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n";return ,[Text.Encoding]::ASCII.GetBytes($text)
}
if($CreateBrowserFixture){$dir=Join-Path $project 'tmp/stage6';[void](New-Item -ItemType Directory -Path $dir -Force);[IO.File]::WriteAllBytes((Join-Path $dir '课程思政联调案例.pdf'),(PDF));Write-Output "BROWSER_FIXTURE=$dir/课程思政联调案例.pdf";return}
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
function SQL([string]$query){
    $local=[IO.File]::ReadAllText((Join-Path $project 'backend/config/application-local.yml'))
    $dbUser=[regex]::Match($local,'(?m)^\s*username:\s*["'']?([^\s"'']+)').Groups[1].Value
    $dbPassword=[regex]::Match($local,'(?m)^\s*password:\s*["'']?([^\s"'']+)').Groups[1].Value
    $previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$dbPassword
    try{$result=@(& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=$dbUser --default-character-set=utf8mb4 --batch --skip-column-names '--database=management-system' --execute=$query);if($LASTEXITCODE -ne 0){throw 'MySQL comparison failed'};return $result}
    finally{if($null -eq $previous){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previous};$dbPassword=$null;$local=$null}
}
function BaseRow($type,$suffix){$p=@{name="S6-$tag-$suffix";description='第六阶段本地开发测试'};if($type -eq 'courses'){$p.courseCode="S6-$tag"};$r=Request $admin POST "/$type" $p;Status 'base create' $r 201;$script:rows+=@{Type=$type;Id=$r.Json.data.id};return [long]$r.Json.data.id}
function CompareStatistics {
    $r=Request $admin GET '/admin/statistics/overview';Status 'statistics overview' $r 200;$s=$r.Json.data
    $visible="status='APPROVED' AND deleted_at IS NULL AND published_at IS NOT NULL"
    $checks=@(
        @{Value=$s.users.total;Sql='SELECT COUNT(*) FROM app_user'},
        @{Value=$s.users.admin;Sql="SELECT COUNT(*) FROM app_user u JOIN role r ON r.id=u.role_id WHERE r.code='ADMIN'"},
        @{Value=$s.users.teacher;Sql="SELECT COUNT(*) FROM app_user u JOIN role r ON r.id=u.role_id WHERE r.code='TEACHER'"},
        @{Value=$s.users.student;Sql="SELECT COUNT(*) FROM app_user u JOIN role r ON r.id=u.role_id WHERE r.code='STUDENT'"},
        @{Value=$s.users.active;Sql="SELECT COUNT(*) FROM app_user WHERE status='ACTIVE'"},
        @{Value=$s.users.disabled;Sql="SELECT COUNT(*) FROM app_user WHERE status='DISABLED'"},
        @{Value=$s.resources.total;Sql='SELECT COUNT(*) FROM teaching_resource WHERE deleted_at IS NULL'},
        @{Value=$s.resources.deleted;Sql='SELECT COUNT(*) FROM teaching_resource WHERE deleted_at IS NOT NULL'},
        @{Value=$s.resources.approved;Sql="SELECT COUNT(*) FROM teaching_resource WHERE $visible"},
        @{Value=$s.usage.browseEvents;Sql='SELECT COUNT(*) FROM browse_record'},
        @{Value=$s.usage.downloadRequests;Sql='SELECT COUNT(*) FROM download_record'},
        @{Value=$s.usage.currentFavorites;Sql="SELECT COUNT(*) FROM favorite f JOIN teaching_resource r ON r.id=f.resource_id WHERE f.active=TRUE AND r.status='APPROVED' AND r.deleted_at IS NULL AND r.published_at IS NOT NULL"})
    foreach($state in @('DRAFT','PENDING','REJECTED')){$checks+=@{Value=$s.resources.($state.ToLower());Sql="SELECT COUNT(*) FROM teaching_resource WHERE deleted_at IS NULL AND status='$state'"}}
    foreach($pair in @(@{Key='courses';Table='course'},@{Key='elements';Table='ideological_element'},@{Key='categories';Table='resource_category'})){
        $checks+=@{Value=$s.($pair.Key).total;Sql="SELECT COUNT(*) FROM $($pair.Table)"}
        $checks+=@{Value=$s.($pair.Key).active;Sql="SELECT COUNT(*) FROM $($pair.Table) WHERE status='ACTIVE'"}
    }
    foreach($check in $checks){Assert 'statistic equals independent SQL' ([long]$check.Value -eq [long](SQL $check.Sql))}
    foreach($pair in @(@{Key='courseDistribution';Table='course';Column='course_id'},@{Key='categoryDistribution';Table='resource_category';Column='category_id'})){
        $expected=@{};foreach($line in (SQL "SELECT b.id,COUNT(p.id) FROM $($pair.Table) b LEFT JOIN (SELECT id,$($pair.Column) FROM teaching_resource WHERE $visible) p ON p.$($pair.Column)=b.id GROUP BY b.id;")){$parts=$line.Split("`t");$expected[$parts[0]]=[long]$parts[1]}
        Assert 'distribution row total' (@($s.($pair.Key)).Count -eq $expected.Count)
        foreach($row in $s.($pair.Key)){Assert 'distribution equals SQL' ($expected[[string]$row.id] -eq [long]$row.resourceCount)}
    }
    $expected=@{};foreach($line in (SQL "SELECT b.id,COUNT(p.resource_id) FROM ideological_element b LEFT JOIN (SELECT x.resource_id,x.element_id FROM resource_element_relation x JOIN teaching_resource r ON r.id=x.resource_id WHERE r.status='APPROVED' AND r.deleted_at IS NULL AND r.published_at IS NOT NULL) p ON p.element_id=b.id GROUP BY b.id;")){$parts=$line.Split("`t");$expected[$parts[0]]=[long]$parts[1]}
    Assert 'element distribution row total' (@($s.elementDistribution).Count -eq $expected.Count)
    foreach($row in $s.elementDistribution){Assert 'element distribution equals SQL' ($expected[[string]$row.id] -eq [long]$row.resourceCount)}
    Assert 'DTO does not leak private fields' (-not ($s|ConvertTo-Json -Depth 10).Contains('password') -and -not ($s|ConvertTo-Json -Depth 10).Contains('storageKey'))
    return $s
}
try{
    $admin=Login 'dev_admin' 'AdminDev#2026';$teacher=Login 'dev_teacher' 'TeacherDev#2026';$student=Login 'dev_student' 'StudentDev#2026';$other=Login 'dev_teacher_b' 'TeacherBDev#2026';$anon=Context;Csrf $anon
    Assert 'Session cookie HttpOnly' (@($admin.Handler.CookieContainer.GetCookies([uri]$BaseUrl)|Where-Object {$_.Name -eq 'JSESSIONID' -and $_.HttpOnly}).Count -eq 1)
    Status 'anonymous statistics' (Request $anon GET '/admin/statistics/overview') 401
    foreach($ctx in @($teacher,$student)){Status 'nonadmin statistics' (Request $ctx GET '/admin/statistics/overview?userId=1') 403}
    $bad=Context;Csrf $bad;Status 'incorrect password generic rejection' (Request $bad POST '/auth/login' @{username='dev_student';password='wrong-password'}) 401
    $newUser=Request $admin POST '/users' @{username="s6_disabled_$tag";displayName='第六阶段禁用测试';role='STUDENT';password='LocalStage6#2026'};Status 'test user create' $newUser 201
    Status 'test user disable' (Request $admin PATCH "/users/$($newUser.Json.data.id)/status" @{status='DISABLED'}) 200
    $disabled=Context;Csrf $disabled;Status 'disabled login denied' (Request $disabled POST '/auth/login' @{username="s6_disabled_$tag";password='LocalStage6#2026'}) 401
    $course=BaseRow courses '课程';$category=BaseRow resource-categories '分类';$e1=BaseRow ideological-elements '元素甲';$e2=BaseRow ideological-elements '元素乙'
    $before=CompareStatistics
    $file=@{Name='课程思政联调案例.pdf';Mime='application/pdf';Bytes=(PDF)}
    $p=@{title="联调案例-$tag";description='本地测试：教学案例与价值引导';courseId=$course;categoryId=$category;elementIds=@()}
    $zero=Request $teacher POST '/teacher/resources' $p $file -Multipart;Status 'zero-element draft' $zero 201;$zeroId=[long]$zero.Json.data.id
    Status 'zero-element submit denied' (Request $teacher POST "/teacher/resources/$zeroId/submit") 400
    $p.elementIds=@($e1,$e2);$draft=Request $teacher POST '/teacher/resources' $p $file -Multipart;Status 'teacher upload' $draft 201;$resourceId=[long]$draft.Json.data.id
    Status 'other teacher cannot edit' (Request $other PUT "/teacher/resources/$resourceId" $p $null -Multipart) 404
    Status 'student teacher endpoint denied' (Request $student GET "/teacher/resources/$resourceId") 403
    Status 'unpublished detail hidden' (Request $student GET "/resources/$resourceId") 404
    Status 'unpublished favorite hidden' (Request $student POST "/resources/$resourceId/favorite") 404
    Status 'submit missing CSRF' (Request $teacher POST "/teacher/resources/$resourceId/submit" -NoCsrf) 403
    Status 'submit first round' (Request $teacher POST "/teacher/resources/$resourceId/submit") 200
    Status 'pending edit denied' (Request $teacher PUT "/teacher/resources/$resourceId" $p $null -Multipart) 409
    Status 'reject first round' (Request $admin POST "/admin/resource-reviews/$resourceId/reject" @{submissionNo=1;reason='请补充课程思政元素说明'}) 200
    $p.description='修改后本地测试案例：补充元素说明';Status 'edit rejected' (Request $teacher PUT "/teacher/resources/$resourceId" $p $file -Multipart) 200
    Status 'resubmit round 2' (Request $teacher POST "/teacher/resources/$resourceId/submit") 200
    Status 'stale round denied' (Request $admin POST "/admin/resource-reviews/$resourceId/approve" @{submissionNo=1}) 409
    Status 'approve round 2' (Request $admin POST "/admin/resource-reviews/$resourceId/approve" @{submissionNo=2}) 200
    Status 'duplicate approve denied' (Request $admin POST "/admin/resource-reviews/$resourceId/approve" @{submissionNo=2}) 409
    $list=Request $student GET "/resources?keyword=$tag&courseId=$course&categoryId=$category&elementId=$e1";Status 'student search' $list 200;Assert 'search only published case' ($list.Json.data.total -eq 1 -and $list.Json.data.items[0].id -eq $resourceId)
    Status 'student detail' (Request $student GET "/resources/$resourceId") 200
    $preview=Request $student GET "/resources/$resourceId/preview";Assert 'preview exact bytes' ($preview.Status -eq 200 -and [Convert]::ToBase64String($preview.Bytes) -eq [Convert]::ToBase64String($file.Bytes))
    Status 'student favorite' (Request $student POST "/resources/$resourceId/favorite") 200
    $download=Request $student GET "/resources/$resourceId/download";Assert 'download exact bytes' ($download.Status -eq 200 -and [Convert]::ToBase64String($download.Bytes) -eq [Convert]::ToBase64String($file.Bytes))
    $after=CompareStatistics
    Assert 'usage delta browse 1 download 1 favorite 1' ($after.usage.browseEvents -eq $before.usage.browseEvents+1 -and $after.usage.downloadRequests -eq $before.usage.downloadRequests+1 -and $after.usage.currentFavorites -eq $before.usage.currentFavorites+1)
    $row=SQL "SELECT status,submission_no,published_at IS NOT NULL,deleted_at IS NULL FROM teaching_resource WHERE id=$resourceId;";Assert 'database published state round2' ($row -eq "APPROVED`t2`t1`t1")
    Assert 'database two elements' ([int](SQL "SELECT COUNT(*) FROM resource_element_relation WHERE resource_id=$resourceId;") -eq 2)
    $audits=SQL "SELECT CONCAT(submission_no,':',decision) FROM audit_record WHERE resource_id=$resourceId ORDER BY submission_no;";Assert 'database immutable two-round history' (($audits -join ',') -eq '1:REJECT,2:APPROVE')
    foreach($table in @('browse_record','download_record','favorite')){Assert "database $table chain" ([int](SQL "SELECT COUNT(*) FROM $table WHERE resource_id=$resourceId;") -eq 1)}
    Assert 'all stored passwords BCrypt' ([int](SQL "SELECT COUNT(*) FROM app_user WHERE password_hash NOT REGEXP '^[$]2[aby][$][0-9]{2}[$].{53}$';") -eq 0)
    Status 'logout' (Request $student POST '/auth/logout') 200;Status 'logged out identity' (Request $student GET '/auth/me') 401
    Write-Host "TOTAL_ASSERTIONS_PASSED=$script:passed HTTP_REQUESTS=$script:requests TEST_TAG=$tag RESOURCE_ID=$resourceId"
}finally{
    $checks=$script:requests
    if($zeroId){try{[void](Request $teacher DELETE "/teacher/resources/$zeroId")}catch{Write-Warning 'Zero draft retained'}}
    foreach($row in $script:rows){try{[void](Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='INACTIVE'})}catch{Write-Warning 'Base fixture retained'}}
    Write-Host "CLEANUP_HTTP_REQUESTS=$($script:requests-$checks)";foreach($ctx in $script:contexts){$ctx.Client.Dispose()}
}
