<# Local development only; retains a labelled published fixture and its audit history. #>
param([string]$BaseUrl='http://127.0.0.1:5173')
$ErrorActionPreference='Stop';Add-Type -AssemblyName System.Net.Http;Add-Type -AssemblyName System.Drawing
$contexts=@();$checks=0;$tag=[guid]::NewGuid().ToString('N').Substring(0,8)
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
 $admin=Login 'dev_admin' 'AdminDev#2026';$teacher=Login 'dev_teacher' 'TeacherDev#2026';$student=Login 'dev_student' 'StudentDev#2026';$other=Login 'dev_teacher_b' 'TeacherBDev#2026';$anon=Context
 $course=(Request $teacher GET '/options/courses').Data[0].id;$category=(Request $teacher GET '/options/resource-categories').Data[0].id;$element=(Request $teacher GET '/options/ideological-elements').Data[0].id
 $pdf=[IO.File]::ReadAllBytes((Join-Path (Split-Path $PSScriptRoot -Parent) 'tmp/stage6/课程思政联调案例.pdf'))
 $body=@{title="资源增强联调-$tag";description='本地开发测试资源，不代表正式课程材料或真实教学效果。';courseId=$course;categoryId=$category;elementIds=@($element)}
 $r=Status 'create own draft' (Request $teacher POST '/teacher/resources' $body @{Name='本地联调.pdf';Mime='application/pdf';Bytes=$pdf}) 201;$id=$r.Data.id
 [void](Status 'student cannot read draft metadata' (Request $student GET "/resources/$id/metadata") 404)
 [void](Status 'other teacher metadata IDOR' (Request $other GET "/teacher/resources/$id/metadata") 404)
 $metadata=@{originalAuthor='本地测试材料提供者';sourceNote='本项目联调生成的测试文件';usageRights='仅用于本地开发验收，不表示对外授权。'}
 [void](Status 'metadata CSRF required' (Request $teacher PUT "/teacher/resources/$id/metadata" $metadata -NoCsrf) 403)
 [void](Status 'save metadata' (Request $teacher PUT "/teacher/resources/$id/metadata" $metadata) 200)
 [void](Status 'bad image rejected' (Request $teacher PUT "/teacher/resources/$id/cover" $null @{Name='fake.png';Mime='image/png';Bytes=[Text.Encoding]::UTF8.GetBytes('not image')}) 400)
 $bitmap=[Drawing.Bitmap]::new(640,360);$g=[Drawing.Graphics]::FromImage($bitmap);$g.Clear([Drawing.Color]::FromArgb(56,85,215));$font=[Drawing.Font]::new('Arial',26);$g.DrawString('LOCAL TEST RESOURCE',$font,[Drawing.Brushes]::White,40,140);$stream=[IO.MemoryStream]::new();$bitmap.Save($stream,[Drawing.Imaging.ImageFormat]::Png);$png=$stream.ToArray();$g.Dispose();$font.Dispose();$bitmap.Dispose();$stream.Dispose()
 [IO.File]::WriteAllBytes((Join-Path (Split-Path $PSScriptRoot -Parent) 'tmp/enhancements/test-cover.png'),$png)
 [void](Status 'cover upload' (Request $teacher PUT "/teacher/resources/$id/cover" $null @{Name='cover.png';Mime='image/png';Bytes=$png}) 200)
 $m=Request $teacher GET "/teacher/resources/$id/metadata";Check 'cover available in own metadata' $m.Data.hasCover
 [void](Status 'teacher reads cover' (Request $teacher GET "/teacher/resources/$id/cover") 200)
 [void](Status 'unpublished cover hidden' (Request $student GET "/resources/$id/cover") 404)
 [void](Status 'remove cover' (Request $teacher DELETE "/teacher/resources/$id/cover") 200)
 [void](Status 'removed cover unavailable' (Request $teacher GET "/teacher/resources/$id/cover") 404)
 [void](Status 'restore cover by upload' (Request $teacher PUT "/teacher/resources/$id/cover" $null @{Name='cover.png';Mime='image/png';Bytes=$png}) 200)
 [void](Status 'submit' (Request $teacher POST "/teacher/resources/$id/submit") 200);[void](Status 'approve' (Request $admin POST "/admin/resource-reviews/$id/approve" @{submissionNo=1}) 200)
 $m=Status 'public metadata' (Request $student GET "/resources/$id/metadata") 200;Check 'metadata actual author' ($m.Data.originalAuthor -eq $metadata.originalAuthor)
 [void](Status 'public cover' (Request $student GET "/resources/$id/cover") 200);[void](Status 'admin review metadata' (Request $admin GET "/admin/resource-reviews/$id/metadata") 200)
 [void](Status 'published metadata immutable' (Request $teacher PUT "/teacher/resources/$id/metadata" $metadata) 409)
 [void](Status 'published cover immutable' (Request $teacher DELETE "/teacher/resources/$id/cover") 409)
 [void](Status 'favorite' (Request $student POST "/resources/$id/favorite") 200)
 $f=Status 'folder create' (Request $student POST '/favorite-folders' @{name="验收资料-$tag"}) 201;$folder=$f.Data.id
 [void](Status 'duplicate folder rejected' (Request $student POST '/favorite-folders' @{name="验收资料-$tag"}) 409)
 [void](Status 'other user cannot rename folder' (Request $other PUT "/favorite-folders/$folder" @{name='越权'}) 404)
 [void](Status 'move favorite' (Request $student PUT "/favorites/$id/folder" @{folderId=$folder}) 200)
 $r=Request $student GET "/favorites?folderId=$folder";Check 'folder filter scoped' ($r.Data.total -eq 1 -and $r.Data.items[0].id -eq $id -and $r.Data.items[0].hasCover)
 [void](Status 'other user cannot filter folder' (Request $other GET "/favorites?folderId=$folder") 404)
 [void](Status 'rename folder' (Request $student PUT "/favorite-folders/$folder" @{name="已整理资料-$tag"}) 200)
 [void](Status 'remove folder only' (Request $student DELETE "/favorite-folders/$folder") 200)
 $r=Request $student GET "/favorites?folderId=0&keyword=$tag";Check 'favorite retained ungrouped' ($r.Data.total -eq 1)
 [void](Status 'admin cannot use personal folders' (Request $admin GET '/favorite-folders') 403)
 [void](Status 'anonymous cannot read cover' (Request $anon GET "/resources/$id/cover") 401)
 Write-Output "ORGANIZATION_CHECKS_PASSED=$checks RESOURCE_ID=$id TEST_TAG=$tag"
} finally {foreach($ctx in $contexts){$ctx.Client.Dispose()}}
