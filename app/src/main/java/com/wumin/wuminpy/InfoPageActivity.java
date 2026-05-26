package com.wumin.wuminpy;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wumin.wuminpy.databinding.ActivityInfoPageBinding;

public class InfoPageActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";

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
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
