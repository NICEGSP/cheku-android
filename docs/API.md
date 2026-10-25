# App ↔ 服务端接口说明

本文档描述车库 App 访问服务端时使用的接口与数据格式，供自建服务端参考。仓库只包含 App 端，不包含服务端实现；文中的 `https://www.example.com` 均为示例地址。

- [通用约定](#通用约定)
- [运行时配置](#1-运行时配置)
- [版本检查与安装包下载](#2-版本检查与安装包下载)
- [传输策略](#3-传输策略)
- [今日油价](#4-今日油价)
- [反馈](#5-反馈)
- [账户](#6-账户)
- [云备份](#7-云备份)

## 通用约定

| 项目 | 约定 |
| --- | --- |
| 根地址 | 构建时的 `CHEKU_WEBSITE_URL`，例如 `https://www.example.com`。运行时配置可以覆盖 `apiBase` / `accountBase` / `backupBase` |
| 协议 | 只接受 HTTPS |
| 请求体 | `Content-Type: application/json; charset=utf-8`（云备份数据上传除外） |
| UA | `CheKu/<versionName> (Android)` |
| 鉴权 | 需要登录的接口带 `Authorization: Bearer <token>` |
| 响应体上限 | JSON 响应不超过 2 MiB |
| 证书锁定 | 配置了 `CHEKU_CERT_PINS` 时，对站点域名及其子域校验证书链上任一级的 SPKI SHA-256 |

**错误响应**：非 2xx 时，App 优先读取 `message`，其次读取 `error` 作为提示文案：

```json
{ "error": "rate_limited", "message": "操作过于频繁", "retryAfterSeconds": 60 }
```

`429` 与 `5xx` 视为可重试；`429` 时会读取 `retryAfterSeconds`（或 `retryAfter`）用于倒计时。

---

## 1. 运行时配置

```
GET {CHEKU_WEBSITE_URL}/api/app/config
```

下发公告、功能开关和各接口地址。App 校验通过后缓存到本地，在 `ttlSeconds` 内不再重复拉取。

```json
{
  "configVersion": 15,
  "ttlSeconds": 86400,
  "endpoints": {
    "apiBase": "https://www.example.com",
    "latestPath": "/api/app/latest",
    "downloadPath": "/api/download",
    "accountBase": "https://www.example.com/api/account",
    "backupBase": "https://www.example.com/api/backup",
    "donationUrl": "",
    "oilPriceSources": ["https://www.autohome.com.cn/oil/"]
  },
  "features": { "accounts": true, "cloudBackup": true },
  "minSupportedVersion": "0.6.0",
  "noticeTitle": "维护通知",
  "notice": "今晚 23:00 起云备份暂停约 1 小时。",
  "issuedAt": "2026-09-22T03:40:00.000Z",
  "sig": "MEUCIQ..."
}
```

### 签名

`sig` 是对 **canonical 串** 的 ECDSA P-256 / SHA-256 签名（DER 编码后 base64）。App 用构建时注入的公钥 `CHEKU_CONFIG_PUBLIC_KEY`（SPKI DER 的 base64）验签。

canonical 串是去掉 `sig` 后的对象按下列**固定键序**序列化的紧凑 JSON（与 JavaScript 的 `JSON.stringify` 输出一致，非 ASCII 字符不转义）：

```
configVersion, ttlSeconds,
endpoints { apiBase, latestPath, downloadPath, accountBase, backupBase, donationUrl, oilPriceSources },
features { accounts, cloudBackup },
minSupportedVersion, noticeTitle, notice, issuedAt
```

### 校验规则

以下任一条不满足，整份配置会被丢弃，App 回落到内置默认值：

1. `apiBase` / `accountBase` / `backupBase` / `oilPriceSources` / 非空的 `donationUrl` 必须是 HTTPS；
2. `apiBase` / `accountBase` / `backupBase` 的主机必须是站点域名本身或其子域（站点域名取自 `CHEKU_WEBSITE_URL`，去掉开头的 `www.`）；
3. `oilPriceSources` 的主机只允许 `www.autohome.com.cn`、`www.icauto.com.cn`；
4. `notice` 不超过 4000 字，`noticeTitle` 不超过 200 字；
5. `ttlSeconds` 在 60 ~ 604800 之间；
6. `latestPath`、`downloadPath` 以 `/` 开头；
7. 签名校验通过；
8. `configVersion` 不小于本地已缓存的版本（防回滚）。

未拉到配置时，`features` 默认在配置了服务端地址时开启、否则关闭。

---

## 2. 版本检查与安装包下载

### 最新版本

```
GET {apiBase}{latestPath}        默认 /api/app/latest
```

```json
{
  "version": "v1.0.1",
  "publishedAt": "2026-10-25T08:00:00Z",
  "notes": "修复若干问题",
  "forceUpdate": false,
  "forceUpdateVersion": "v0.9.0",
  "downloadUrl": "https://www.example.com/api/download"
}
```

| 字段 | 说明 |
| --- | --- |
| `version` | 必填。按其中的数字段逐段比较新旧 |
| `forceUpdate` | 为 `true` 时强制更新 |
| `forceUpdateVersion` | 当前版本低于它时强制更新 |
| `publishedAt` / `downloadUrl` | 可选 |

### 下载安装包

```
GET {apiBase}{downloadPath}      默认 /api/download
```

返回 APK 字节流。允许 3xx 重定向（最多 5 次，每一跳都必须是 HTTPS），安装包不超过 250 MiB。App 会先发 `HEAD`（或 `Range: bytes=0-0`）探测体积，用于更新弹窗中展示大小。

---

## 3. 传输策略

```
GET {apiBase}/api/app/policy
```

```json
{ "backupRateLimitKbps": 512 }
```

云备份上传 / 下载的限速提示，`-1` 或缺省表示未知。

---

## 4. 今日油价

```
GET {apiBase}/api/app/oil-prices?province=北京
```

`province` 可省略。油号固定为 `92`、`95`、`98`、`0`。

```json
{
  "region": { "province": "北京" },
  "updatedAt": "2026-10-18T03:39:50.854Z",
  "source": "autohome",
  "grades": [
    { "grade": "92", "price": 8.61, "delta": 0.32, "trend": "up" }
  ],
  "history": [
    { "date": "2026-09-25", "92": 8.29, "95": 8.83, "98": 10.33, "0": 8.02 }
  ],
  "adjustment": {
    "lastDate": "2026-09-25",
    "nextDate": "2026-10-23",
    "daysLeft": 5,
    "elapsedDays": 23,
    "windowDays": 10,
    "forecast": { "92": 8.75, "95": 9.31, "98": 10.81, "0": 8.49 },
    "forecastDelta": 0.14,
    "forecastDirection": "up"
  }
}
```

`grades` 为空或缺失时视为无数据；`trend` / `forecastDirection` 取值 `up` / `down` / `flat`。

---

## 5. 反馈

```
POST {apiBase}/api/app/feedback
```

```json
{ "text": "希望增加……", "version": "1.0.1" }
```

| 状态码 | 含义 |
| --- | --- |
| 2xx | 提交成功 |
| 400 | 内容不合法，`error` 为原因 |
| 429 | 超出频率限制（App 约定每台设备每天一条），`error` 为提示文案 |

---

## 6. 账户

根地址 `{accountBase}`，默认 `{CHEKU_WEBSITE_URL}/api/account`。邮箱在发送前会去掉首尾空白并转为小写。

| 方法 | 路径 | 请求体 | 鉴权 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/register/send-code` | `{ "email" }` | | 发送注册验证码 |
| POST | `/register` | `{ "email", "code", "password" }` | | 注册，成功返回会话 |
| POST | `/login` | `{ "email", "password" }` | | 登录，返回会话 |
| POST | `/logout` | — | ✓ | 退出登录 |
| GET | `/me` | — | ✓ | 账户信息 |
| POST | `/delete` | `{ "password" }` | ✓ | 申请注销（进入宽限期） |
| POST | `/delete/cancel` | `{}` | ✓ | 撤销注销 |
| POST | `/password/reset-code` | `{ "email" }` | | 发送重置密码验证码 |
| POST | `/password/reset` | `{ "email", "code", "password" }` | | 重置密码 |

验证码为 4 ~ 8 位数字，密码长度 8 ~ 128。

**会话**（`/register`、`/login` 的响应；`/register` 不返回 `token` 时 App 会自动再调一次 `/login`）：

```json
{ "token": "eyJ...", "expiresAt": 1893456000, "uid": "u_abc", "email": "user@example.com" }
```

`expiresAt` 可以是秒级或毫秒级时间戳，也可以是 ISO 8601 字符串。

**账户信息**（`/me`）：

```json
{
  "uid": "u_abc",
  "email": "user@example.com",
  "emailVerified": true,
  "createdAt": "2026-09-01T10:00:00Z",
  "lastLoginAt": "2026-10-25T08:00:00Z",
  "status": "active",
  "quotaBytes": 16777216,
  "deletionScheduledAt": 0
}
```

`/delete` 的响应可附带 `deletionScheduledAt`（毫秒时间戳）以及新的会话字段。

| 状态码 | App 提示 |
| --- | --- |
| 401 | 邮箱或密码错误 |
| 403 | 操作被拒绝（附 `message`） |
| 409 | 邮箱已被注册 |
| 429 | 操作过于频繁（读取 `retryAfterSeconds` 倒计时） |

---

## 7. 云备份

根地址 `{backupBase}`，默认 `{CHEKU_WEBSITE_URL}/api/backup`，全部需要登录。每个账户只保留一份备份，新上传覆盖旧的；单份上限 16 MiB。上传内容是 App 导出的 `.che` 主包（已加密），不包含人车证书包。

### 查询

```
GET /meta
```

```json
{
  "uid": "u_abc",
  "meta": {
    "bytes": 524288,
    "uploadedAt": "2026-10-25T08:00:00Z",
    "manifest": { "createdAt": "2026-10-25T07:59:30Z", "appVersion": "v1.0.1" }
  }
}
```

没有备份时返回 404。

### 上传（三步）

1. **开始**：`POST /begin`，请求体 `{ "sizeBytes": 524288 }`，返回 `{ "uploadId": "up_123" }`；
2. **传数据**：`POST /data?uploadId=up_123`，`Content-Type: application/octet-stream`，请求体为备份文件原始字节；
3. **提交**：`POST /commit?uploadId=up_123`，请求体：

   ```json
   { "uploadId": "up_123", "sha256": "<文件内容 SHA-256 的小写十六进制>", "bytes": 524288 }
   ```

   返回 `{ "ok": true, "sizeBytes": 524288 }`。

### 下载与清空

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/download` | 返回备份文件字节流（`application/octet-stream`） |
| POST | `/purge` | 删除云端备份，账户保留 |

| 状态码 | 含义 |
| --- | --- |
| 400 | 请求被拒绝；响应中含 `excluded` / `已排除` / `证书包` 等字样时，表示备份里包含不允许上传的证书数据 |
| 401 / 403 | 登录失效 |
| 404 | 上传会话已失效（上传时），或云端没有备份（下载时） |
| 413 | 超过 16 MiB |
| 429 | 操作过于频繁 |
