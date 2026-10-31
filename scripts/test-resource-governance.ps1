<# Local development only. Creates a labelled fixture; preserves audit/publication/feedback history.
   Finally restores its own fixture if an assertion fails. Never withdraws an existing user's resource. #>
param([string]$BaseUrl='http://127.0.0.1:5173')
$ErrorActionPreference='Stop';Add-Type -AssemblyName System.Net.Http
$contexts=@();$checks=0;$tag=[guid]::NewGuid().ToString('N').Substring(0,8);$id=0;$admin=$null
function Check($name,$value){if(-not $value){throw "FAIL: $name"};$script:checks++;Write-Host "PASS: $name"}
function Context{$h=[Net.Http.HttpClientHandler]::new();$h.CookieContainer=[Net.CookieContainer]::new();$c=@{Client=[Net.Http.HttpClient]::new($h);Token=$null;Header=$null};$script:contexts+=,$c;return $c}
function Request($ctx,$method,$path,$body=$null,$file=$null,[switch]$NoCsrf){
 $req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method),"$BaseUrl/api$path")
 if($method -notin @('GET','HEAD') -and $ctx.Token -and -not $NoCsrf){[void]$req.Headers.TryAddWithoutValidation($ctx.Header,$ctx.Token)}
 if($file){$content=[Net.Http.MultipartFormDataContent]::new();if($body){$content.Add([Net.Http.StringContent]::new(($body|ConvertTo-Json -Depth 10),[Text.Encoding]::UTF8,'application/json'),'metadata')};$part=[Net.Http.ByteArrayContent]::new([byte[]]$file.Bytes);$part.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new($file.Mime);$content.Add($part,'file',$file.Name);$req.Content=$content}
 elseif($null -ne $body){$req.Content=[Net.Http.StringContent]::new(($body|ConvertTo-Json -Depth 10),[Text.Encoding]::UTF8,'application/json')}
 $r=$ctx.Client.SendAsync($req).GetAwaiter().GetResult();try{$bytes=$r.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult();$json=$null;try{$json=[Text.Encoding]::UTF8.GetString($bytes)|ConvertFrom-Json}catch{};return @{Status=[int]$r.StatusCode;Data=$json.data;Bytes=$bytes}}finally{$r.Dispose();$req.Dispose()}
}
function Status($name,$r,$expected){Check "$name HTTP $($r.Status)" ($r.Status -eq $expected);return $r}
function Login($name,$password){$ctx=Context;$r=Request $ctx GET '/auth/csrf';$ctx.Token=$r.Data.token;$ctx.Header=$r.Data.headerName;[void](Status "$name login" (Request $ctx POST '/auth/login' @{username=$name;password=$password}) 200);$r=Request $ctx GET '/auth/csrf';$ctx.Token=$r.Data.token;$ctx.Header=$r.Data.headerName;return $ctx}
try {
 $admin=Login 'dev_admin' 'AdminDev#2026';$teacher=Login 'dev_teacher' 'TeacherDev#2026';$student=Login 'dev_student' 'StudentDev#2026';$anon=Context
 $course=(Request $teacher GET '/options/courses').Data[0].id;$category=(Request $teacher GET '/options/resource-categories').Data[0].id;$element=(Request $teacher GET '/options/ideological-elements').Data[0].id
 $root=Split-Path $PSScriptRoot -Parent
 $pdf=[IO.File]::ReadAllBytes((Join-Path $root 'tmp/stage6/课程思政联调案例.pdf'))
 $body=@{title="资源治理联调-$tag";description='本地验收夹具，不代表正式教学内容或效果。';courseId=$course;categoryId=$category;elementIds=@($element)}
 $r=Status 'create own fixture' (Request $teacher POST '/teacher/resources' $body @{Name='governance.pdf';Mime='application/pdf';Bytes=$pdf}) 201;$id=$r.Data.id
 [void](Status 'draft cannot withdraw' (Request $admin POST "/admin/published-resources/$id/withdraw" @{reason='尚未审核'}) 404)
 [void](Status 'draft cannot report' (Request $student POST "/resources/$id/feedback" @{kind='FILE';description='不可见'}) 404)
 $metadata=@{originalAuthor='本地验收材料';sourceNote='测试脚本生成';usageRights='仅用于本地测试'}
 [void](Status 'metadata fixture' (Request $teacher PUT "/teacher/resources/$id/metadata" $metadata) 200)
 $png=[IO.File]::ReadAllBytes((Join-Path $root 'tmp/enhancements/test-cover.png'))
 [void](Status 'cover fixture' (Request $teacher PUT "/teacher/resources/$id/cover" $null @{Name='cover.png';Mime='image/png';Bytes=$png}) 200)
 [void](Status 'submit' (Request $teacher POST "/teacher/resources/$id/submit") 200);[void](Status 'approve' (Request $admin POST "/admin/resource-reviews/$id/approve" @{submissionNo=1}) 200)
 $published=(Request $student GET "/resources/$id").Data.publishedAt
 [void](Status 'download before withdrawal' (Request $student GET "/resources/$id/download") 200)
 [void](Status 'favorite before withdrawal' (Request $student POST "/resources/$id/favorite") 200)
 $report=@{kind='RIGHTS';description="联调反馈-$tag：请核对测试授权"}
 [void](Status 'report CSRF' (Request $student POST "/resources/$id/feedback" $report -NoCsrf) 403)
 [void](Status 'invalid kind' (Request $student POST "/resources/$id/feedback" @{kind='INVALID';description='说明'}) 400)
 [void](Status 'blank description' (Request $student POST "/resources/$id/feedback" @{kind='FILE';description=' '}) 400)
 [void](Status 'report created' (Request $student POST "/resources/$id/feedback" $report) 200)
 [void](Status 'duplicate open report' (Request $student POST "/resources/$id/feedback" $report) 409)
 $mine=(Request $student GET "/resource-feedback/mine?resourceId=$id").Data;Check 'own report returned' ($mine.total -eq 1);$feedback=$mine.items[0].id
 Check 'teacher cannot see student report' ((Request $teacher GET "/resource-feedback/mine?resourceId=$id&userId=1").Data.total -eq 0)
 foreach($ctx in @($student,$teacher)){
  [void](Status 'nonadmin feedback list' (Request $ctx GET '/admin/resource-feedback') 403)
  [void](Status 'nonadmin handle' (Request $ctx POST "/admin/resource-feedback/$feedback/handle" @{status='RESOLVED';response='越权'}) 403)
  [void](Status 'nonadmin withdrawal' (Request $ctx POST "/admin/published-resources/$id/withdraw" @{reason='越权'}) 403)
  [void](Status 'nonadmin restoration' (Request $ctx POST "/admin/published-resources/$id/restore" @{reason='越权'}) 403)
 }
 [void](Status 'anonymous report' (Request $anon GET '/resource-feedback/mine') 401)
 [void](Status 'admin not consumer' (Request $admin POST "/resources/$id/feedback" $report) 403)
 [void](Status 'invalid ledger filter' (Request $admin GET '/admin/published-resources?publication=UNKNOWN') 400)
 [void](Status 'blank withdrawal reason' (Request $admin POST "/admin/published-resources/$id/withdraw" @{reason=' '}) 400)
 [void](Status 'withdraw CSRF' (Request $admin POST "/admin/published-resources/$id/withdraw" @{reason='核查'} -NoCsrf) 403)
 $before=(Request $admin GET '/admin/statistics/overview').Data
 $audits=(Request $admin GET "/admin/resource-reviews/$id").Data.auditRecords|ConvertTo-Json -Depth 10 -Compress
 $down=Status 'withdraw' (Request $admin POST "/admin/published-resources/$id/withdraw" @{reason="联调下架-$tag"}) 200
 Check 'withdraw event recorded' ($down.Data.withdrawnAt -and $down.Data.events.Count -eq 1 -and $down.Data.events[0].action -eq 'WITHDRAW')
 [void](Status 'repeat withdrawal conflicts' (Request $admin POST "/admin/published-resources/$id/withdraw" @{reason='重复'}) 409)
 foreach($path in @("/resources/$id","/resources/$id/preview","/resources/$id/download","/resources/$id/cover","/resources/$id/metadata","/portal/resources/$id")){
  [void](Status "withdrawn hidden $path" (Request $student GET $path) 404)
 }
 [void](Status 'withdrawn favorite unavailable' (Request $student POST "/resources/$id/favorite") 404)
 [void](Status 'withdrawn report unavailable' (Request $student POST "/resources/$id/feedback" $report) 404)
 Check 'withdrawn search hidden' ((Request $student GET "/resources?keyword=$tag").Data.total -eq 0)
 Check 'withdrawn favorites hidden' ((Request $student GET "/favorites?keyword=$tag").Data.total -eq 0)
 foreach($path in @('/history/browse?size=100','/history/downloads?size=100')){Check "withdrawn history filtered $path" (@((Request $student GET $path).Data.items|Where-Object {$_.resourceId -eq $id}).Count -eq 0)}
 $after=(Request $admin GET '/admin/statistics/overview').Data
 Check 'published decreases withdrawn increases' ($after.resources.approved -eq $before.resources.approved-1 -and $after.resources.withdrawn -eq $before.resources.withdrawn+1)
 Check 'history totals preserved favorite availability decremented' ($after.usage.browseEvents -eq $before.usage.browseEvents -and $after.usage.downloadRequests -eq $before.usage.downloadRequests -and $after.usage.currentFavorites -eq $before.usage.currentFavorites-1)
 foreach($key in @('courseDistribution','categoryDistribution','elementDistribution')){$target=if($key -eq 'courseDistribution'){$course}elseif($key -eq 'categoryDistribution'){$category}else{$element};$a=@($after.$key|Where-Object {$_.id -eq $target})[0].resourceCount;$b=@($before.$key|Where-Object {$_.id -eq $target})[0].resourceCount;Check "$key excludes withdrawal" ($a -eq $b-1)}
 Check 'withdrawn ledger available' ((Request $admin GET "/admin/published-resources?keyword=$tag&publication=WITHDRAWN").Data.total -eq 1)
 Check 'live ledger excludes withdrawal' ((Request $admin GET "/admin/published-resources?keyword=$tag&publication=LIVE").Data.total -eq 0)
 [void](Status 'teacher body stays immutable' (Request $teacher PUT "/teacher/resources/$id" $body @{Name='governance.pdf';Mime='application/pdf';Bytes=$pdf}) 409)
 [void](Status 'teacher metadata stays immutable' (Request $teacher PUT "/teacher/resources/$id/metadata" $metadata) 409)
 [void](Status 'teacher cover stays immutable' (Request $teacher DELETE "/teacher/resources/$id/cover") 409)
 [void](Status 'admin still reads original attachment' (Request $admin GET "/admin/resource-reviews/$id/attachment") 200)
 Check 'audit records unchanged by withdrawal' (((Request $admin GET "/admin/resource-reviews/$id").Data.auditRecords|ConvertTo-Json -Depth 10 -Compress) -eq $audits)
 Check 'feedback readable while withdrawn' ((Request $student GET "/resource-feedback/mine?resourceId=$id").Data.total -eq 1)
 [void](Status 'handle invalid state' (Request $admin POST "/admin/resource-feedback/$feedback/handle" @{status='OPEN';response='无效'}) 400)
 [void](Status 'handle needs response' (Request $admin POST "/admin/resource-feedback/$feedback/handle" @{status='RESOLVED';response=' '}) 400)
 [void](Status 'handle CSRF' (Request $admin POST "/admin/resource-feedback/$feedback/handle" @{status='RESOLVED';response='核查'} -NoCsrf) 403)
 [void](Status 'admin resolves feedback' (Request $admin POST "/admin/resource-feedback/$feedback/handle" @{status='RESOLVED';response='测试处理：已核查，无正式教学授权含义。'}) 200)
 [void](Status 'cannot override handling' (Request $admin POST "/admin/resource-feedback/$feedback/handle" @{status='DISMISSED';response='覆盖'}) 409)
 $handled=(Request $student GET "/resource-feedback/mine?resourceId=$id").Data.items[0];Check 'reporter sees actual response and handler time' ($handled.status -eq 'RESOLVED' -and $handled.handledAt -and $handled.handlerName)
 $restored=Status 'restore' (Request $admin POST "/admin/published-resources/$id/restore" @{reason='本地验收恢复原内容'}) 200
 Check 'events ordered and current flag clear' (-not $restored.Data.withdrawnAt -and ($restored.Data.events.action -join ',') -eq 'WITHDRAW,RESTORE')
 [void](Status 'repeat restore conflicts' (Request $admin POST "/admin/published-resources/$id/restore" @{reason=''}) 409)
 Check 'original publish time unchanged' ((Request $student GET "/resources/$id").Data.publishedAt -eq $published)
 Check 'original favorite reappears' ((Request $student GET "/favorites?keyword=$tag").Data.total -eq 1)
 $file=Status 'restored original download' (Request $student GET "/resources/$id/download") 200;Check 'original file bytes retained' ([Convert]::ToBase64String($file.Bytes) -eq [Convert]::ToBase64String($pdf))
 [void](Status 'cover restored without reupload' (Request $student GET "/resources/$id/cover") 200)
 $raceRequests=@();$raceResponses=@();$raceTasks=@()
 try {
  for($i=0;$i -lt 2;$i++){
   $req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post,"$BaseUrl/api/admin/published-resources/$id/withdraw")
   [void]$req.Headers.TryAddWithoutValidation($admin.Header,$admin.Token)
   $req.Content=[Net.Http.StringContent]::new((@{reason="本地并发下架验收-$tag"}|ConvertTo-Json),[Text.Encoding]::UTF8,'application/json')
   $raceRequests+=,$req;$raceTasks+=,$admin.Client.SendAsync($req)
  }
  foreach($task in $raceTasks){$raceResponses+=,$task.GetAwaiter().GetResult()}
  Check 'concurrent withdrawal serializes to one success and one conflict' ((($raceResponses|ForEach-Object {[int]$_.StatusCode}|Sort-Object) -join ',') -eq '200,409')
 } finally {foreach($r in $raceResponses){$r.Dispose()};foreach($r in $raceRequests){$r.Dispose()}}
 [void](Status 'restore concurrent fixture' (Request $admin POST "/admin/published-resources/$id/restore" @{reason='并发验收结束，恢复原内容'}) 200)
 $events=(Request $admin GET "/admin/published-resources/$id/publication").Data.events
 Check 'concurrent duplicate leaves only one event' (($events.action -join ',') -eq 'WITHDRAW,RESTORE,WITHDRAW,RESTORE')
 Write-Output "GOVERNANCE_CHECKS_PASSED=$checks RESOURCE_ID=$id TEST_TAG=$tag"
} finally {
 if($id -and $admin){try{$p=Request $admin GET "/admin/published-resources/$id/publication";if($p.Data.withdrawnAt){[void](Request $admin POST "/admin/published-resources/$id/restore" @{reason='本地自动测试清理：恢复自己的测试夹具'})}}catch{Write-Warning 'Own fixture may remain withdrawn; inspect test resource only.'}}
 foreach($ctx in $contexts){$ctx.Client.Dispose()}
}
