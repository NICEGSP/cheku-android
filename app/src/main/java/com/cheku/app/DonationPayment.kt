package com.cheku.app

import android.content.ClipData
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cheku.app.config.RuntimeConfigProvider
import java.io.File

/** 内置默认赞助地址 */
private const val DEFAULT_ALIPAY_DONATION_QR = "https://qr.alipay.com/45t12153efb71whdffe51ee"

fun openAlipayPayment(context: Context) {
    // 赞助地址优先取运行时配置，未配置时回落到内置默认值。
    val qrcode = RuntimeConfigProvider.donationUrl().ifBlank { DEFAULT_ALIPAY_DONATION_QR }
    val deepLink = Uri.Builder()
        .scheme("alipays")
        .authority("platformapi")
        .appendPath("startapp")
        .appendQueryParameter("saId", "10000007")
        .appendQueryParameter("qrcode", qrcode)
        .build()
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, deepLink).apply {
            setPackage("com.eg.android.AlipayGphone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        Toast.makeText(context, "未安装支付宝或支付宝无法打开收款码", Toast.LENGTH_SHORT).show()
    }
}

fun openWechatDonationQrInSystemScanner(context: Context) {
    runCatching {
        val image = File(context.cacheDir, "payment_images/wechat_donation_qr.png").apply {
            parentFile?.mkdirs()
            val resourceUri = Uri.parse("android.resource://${context.packageName}/${R.drawable.wechat_donation_qr}")
            context.contentResolver.openInputStream(resourceUri)?.use { input ->
                outputStream().use(input::copyTo)
            } ?: error("无法读取二维码资源")
        }

        saveWechatDonationQrToGallery(context, image)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
        val imageIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "微信赞助二维码", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val scannerIntent = Intent(imageIntent).apply {
            component = ComponentName("com.xiaomi.scanner", "com.xiaomi.scanner.app.ScanActivity")
        }
        context.grantUriPermission("com.xiaomi.scanner", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(scannerIntent) }.getOrElse {
            context.startActivity(Intent.createChooser(imageIntent, "选择系统扫码识别二维码"))
        }
        Toast.makeText(context, "二维码已保存到相册", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "无法调用系统扫码：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
    }
}

private fun saveWechatDonationQrToGallery(context: Context, image: File) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "cheku_wechat_donation_qr.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CheKu")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val uri = context.contentResolver.insert(collection, values) ?: error("无法写入系统相册")
    try {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            image.inputStream().use { input -> input.copyTo(output) }
        } ?: error("无法写入二维码图片")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        }
    } catch (error: Throwable) {
        context.contentResolver.delete(uri, null, null)
        throw error
    }
}
