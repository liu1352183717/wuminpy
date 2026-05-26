package com.wumin.wuminpy;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wumin.wuminpy.databinding.ActivitySponsorBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SponsorActivity extends AppCompatActivity {

    private static final Pattern QQ_PATTERN = Pattern.compile("QQ[：:]\\s*(\\d{5,12})");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySponsorBinding binding = ActivitySponsorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.ivWechatQr.setOnLongClickListener(v -> {
            saveImageToGallery(R.drawable.sponsor_wechat, "wuminpy_wechat_qr.jpg", "微信赞赏码");
            return true;
        });

        binding.ivAlipayQr.setOnLongClickListener(v -> {
            saveImageToGallery(R.drawable.sponsor_alipay, "wuminpy_alipay_qr.jpg", "支付宝赞赏码");
            return true;
        });

        binding.tvContact.setMovementMethod(LinkMovementMethod.getInstance());
        Linkify.addLinks(binding.tvContact, QQ_PATTERN, null, null, (match, url) -> {
            Matcher m = QQ_PATTERN.matcher(match.toString());
            if (m.find()) {
                return "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=" + m.group(1);
            }
            return url;
        });
    }

    private void saveImageToGallery(int drawableId, String fileName, String label) {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), drawableId);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WuminPy");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out != null) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                        }
                    }
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }
                Toast.makeText(this, label + " 已保存到相册 → Pictures/WuminPy/" + fileName, Toast.LENGTH_LONG).show();
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WuminPy");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                }
                MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), fileName, null);
                Toast.makeText(this, label + " 已保存到 " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
