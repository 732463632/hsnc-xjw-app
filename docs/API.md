# 接口与解析说明

站点：`http://xjw.hsnc.edu.cn:7777`（仅 HTTP，未提供 HTTPS）
后台：nginx + Spring Security 表单登录，URP 系列（清元优软）

所有请求都依赖登录后的会话 Cookie，没有独立的 API 鉴权，因此客户端是「先登录、再带 Cookie 请求页面」的模式。

---

## 1. 登录

```
GET  /login                  取隐藏字段 tokenValue、schoolId
GET  /img/captcha.jpg        验证码图片，与当前会话绑定
POST /j_spring_security_check
     lang=zh_CN
     tokenValue=<页面里的 token>
     j_username=<学号>
     j_password=<见下>
     j_captcha=<4 位验证码>
```

`j_password` 不是明文，而是按登录页 `md5.min.js` 的规则计算的双重 MD5 字符串，形如 `<hashA>*<hashB>`。实现见 `data/UrpApi.kt` 的 `hashPassword()`。

登录失败时，服务端会返回带错误文案的登录页（例如验证码错误），而不是 JSON，需要按 HTML 解析错误信息。

实测定到的三类失败，区分方式如下（最终都会落在登录页上，靠 errorCode 和页面文案区分）：

| 情况 | 判定依据 | 能否直接重试 |
|---|---|---|
| 验证码错 | `302 → /login?errorCode=badCaptcha`，或页面出现 `验证码错误` | 可以，换一张图重试 |
| 账号或密码错 | `302 → /login?errorCode=badCredentials`，页面提示 `用户名或密码错误!` | 重试无意义，密码可能已改 |
| 账号被锁 | 页面提示 `账号已被锁定！` | 不要重试，会加重锁定 |

几个坑：

1. **失败时不一定重定向**。有时直接 200 返回登录页，所以只看 URL 会把失败当成功；必须同时检查响应体里是否还有登录表单（`id="input_password"` / `name="j_password"`）。
2. **提示文案前缀固定是「发生错误！」**，按 `class="alert"` 取到 div 后要去掉这个前缀和换行，否则用户只能看到一行笼统的「登录失败」。
3. 账号被锁的判定顺序在验证码**之后**：验证码错了会先报 badCaptcha，所以连续锁定时看不到锁定提示，别误以为验证码一直错。

## 2. 成绩

```
GET  /student/integratedQuery/scoreQuery/allPassingScores/index
GET  <页面里 var url = "..." 的地址>            ← 同一会话内一次有效
```

**注意**：成绩页里的回调地址中含有每次刷新都会变化的随机段，且只能用一次。复用旧地址会返回：

```json
{"result":"error","msg":"非法请求！！！"}
```

所以必须「先取页面 → 立刻解析出地址 → 马上请求」。响应结构：

```
lnList[]           按学期分组的成绩
  cjlx             学期名，如 “2025-2026学年春”
  cjList[]         课程列表
    courseName / credit / courseScore / gradePointScore / courseAttributeName
```

加权平均绩点 = Σ(课程绩点 × 学分) / Σ(学分)，只统计有绩点的课程。

## 3. 学业完成情况（培养方案）

```
GET /student/integratedQuery/planCompletion/index
```

服务端直出 HTML，模块树以 JS 变量形式内嵌：

```js
var zNodes = [ { id, pId, name, flagType, sfwc }, ... ];
```

- `flagType == "002"` 是模块节点，`name` 里内嵌统计文本：
  `最低修读学分:45.8,通过学分:42.75,已修课程门数:26,...`
- `flagType == "kch"` 是课程节点，`name` 形如
  `[HB171003]课程名[3学分,必修](建议学期,成绩)`，无成绩即未通过
- `sfwc` 表示模块是否完成

课程号与课表接口的 `coureNumber` 格式一致，可直接用于匹配「本学期在修」。

## 4. 课表

```
GET /ajaxStudentSchedule/weekLySchedule/callback/{week}
```

**按周返回**。只拉一次会漏课——某一周可能只有 4 门课有安排，另一周有 8 门。客户端因此做了周次切换。

```
xkxx[0]            以课程号为 key 的 map
  courseName / attendClassTeacher / unit / courseAttributeName
  timeAndPlaceList[]   一个课程可能有多个时段
    classDay           星期几（1-7）
    classSessions      起始节次
    continuingSession  连续节数
    weekDescription    周次范围
    teachingBuildingName / classroomName
```

## 5. 节次时间

```
POST /ajax/student/getSectionAndTime
     planNumber=<培养方案号>&ff=j&xqh=<校区>
```

实测该接口只返回节次配置，`sectionTime` 字段为空——**学校没有在系统里配置上课时间**，所以官方课表页也只显示「第 6-7 节」。

客户端改用学校作息时间表的内置映射（精确到小节，1-13 节），并在 5 月 1 日至 8 月底自动切换到夏季作息（下午与晚上整体后移 30 分钟）。映射见 `data/SectionTime.kt`。

## 6. 考试安排

```
GET /student/examinationManagement/examPlan/detail?start=yyyy-MM-dd&end=yyyy-MM-dd
```

供 FullCalendar 使用。**当前学期该接口返回的是一个孤立的 `]` 字符**（1 字节，不是合法 JSON），因为学校尚未排考且服务端序列化有缺陷。解析层按空数组处理。

---

## 解析层的容错约定

教务系统返回格式不稳定，客户端在以下情况都会降级而不是崩溃：

- JSON 不合法（如上面考试接口）→ 视为空列表
- 会话过期被重定向回登录页 → 抛 `SessionExpiredException`，界面退回登录
- 页面结构变化导致取不到回调地址 → 抛 `UrpException`，给出可读提示
- 字段缺失（`optString` / `optDouble` + 默认值）→ 跳过该条，不中断整页
