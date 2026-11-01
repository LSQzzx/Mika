# 按提交消息自动发布 Android Release

`.github/workflows/release.yml` 在推送到 `main` 时检查最后一条提交消息。包含 `[release]` 就测试、构建、签名并发布 Release；其他提交跳过打包。提交消息只用于判断，不作为 shell 命令执行。

先把包含此工作流的分支合并到 `main`。发布时修改 `app/build.gradle.kts`，同时增加两个值，例如：

```kotlin
versionCode = 32
versionName = "0.1.31"
```

然后在 `main` 上提交并推送：

```sh
git add app/build.gradle.kts
git commit -m "[release] 发布 0.1.31"
git push origin main
```

一次推送多个提交时，只看最后一条消息；使用合并或 squash 提交时，最终提交消息也要保留 `[release]`。

## 首次配置签名

仓库 Settings → Secrets and variables → Actions 中需要两个 repository secrets：

| Secret | 内容 |
|---|---|
| `MIKA_RELEASE_KEYSTORE_BASE64` | 之前本地 release 签名文件 `local-release.p12` 的 Base64 |
| `MIKA_RELEASE_KEYSTORE_PASSWORD` | 同一签名文件的密码 |

工作流使用既有的 `mika-local-release` 别名，密码同时用于 keystore 和私钥。还会校验证书 SHA-256 指纹：

```text
bc101a5988af967272eae79c02101ee9fdee7f446355a4e996b8254855e52e76
```

这样云端 APK 可覆盖此前的本地签名包。私钥不会进入 Git 仓库、缓存或 Release，临时签名文件在任务结束时删除。

在已登录 GitHub CLI 的电脑上可用下列命令配置，无需打印密钥或密码：

```sh
base64 < ~/.local/share/mika/signing/local-release.p12 | tr -d '\n' |
  gh secret set MIKA_RELEASE_KEYSTORE_BASE64 --repo LSQzzx/Mika
gh secret set MIKA_RELEASE_KEYSTORE_PASSWORD --repo LSQzzx/Mika \
  < ~/.local/share/mika/signing/local-release.password
```

这是将长期签名私钥保存在 GitHub 的加密 Secrets 中；只给受信任的人仓库写入权限。未配置签名时，工作流会失败，不会发布未签名的包。

## 发布内容和失败重试

- tag 为 `v<versionName>`，例如 `v0.1.31`，指向触发构建的提交。
- 附件为 `Mika-v0.1.31.apk` 和对应的 `.sha256` 校验文件。
- 先运行 core/app 单元测试和 release 构建，再校验实际 APK 版本、签名与 ZIP 对齐。
- 附件先上传到草稿 Release，成功后才公开并标记为 Latest，App 的检查更新便可发现它。
- 已公开的 Release 不会被覆盖。版本号和版本码不递增，或者同名 tag 指向其他提交时会拒绝发布。
- 上传失败留下草稿时可重跑工作流；若 tag 已存在，只允许重跑相同提交，不会移动旧 tag。

版本检查的本地测试：

```sh
python3 -m unittest discover -s tools/release -v
```
