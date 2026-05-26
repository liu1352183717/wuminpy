package com.wumin.wuminpy;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;

import androidx.appcompat.app.AppCompatActivity;

import com.wumin.wuminpy.databinding.ActivityInfoPageBinding;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InfoPageActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";

    private static final Pattern QQ_PATTERN = Pattern.compile("QQ[：:]\\s*(\\d{5,12})");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityInfoPageBinding binding = ActivityInfoPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String content = getIntent().getStringExtra(EXTRA_CONTENT);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title != null ? title : "");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.tvContent.setMovementMethod(LinkMovementMethod.getInstance());
        binding.tvContent.setText(content != null ? content : "");

        Linkify.addLinks(binding.tvContent, QQ_PATTERN, null, null, (match, url) -> {
            Matcher m = QQ_PATTERN.matcher(match.toString());
            if (m.find()) {
                return "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=" + m.group(1);
            }
            return url;
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
