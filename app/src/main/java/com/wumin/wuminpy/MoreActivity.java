package com.wumin.wuminpy;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wumin.core.AppConfig;

public class MoreActivity extends AppCompatActivity {

    private TextInputEditText etPipMirror;
    private MaterialSwitch swLogShowInternal;
    private TextInputEditText etScriptPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_setting);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        TextInputLayout pipMirrorLayout = findViewById(R.id.pip_mirror);
        etPipMirror = (TextInputEditText) pipMirrorLayout.getEditText();

        swLogShowInternal = findViewById(R.id.log_show_all);

        TextInputLayout scriptPathLayout = findViewById(R.id.script_path);
        etScriptPath = (TextInputEditText) scriptPathLayout.getEditText();
    }

    private void loadSettings() {
        etPipMirror.setText(AppConfig.INSTANCE.getPipMirror());
        swLogShowInternal.setChecked(AppConfig.INSTANCE.getLogShowInternal());
        etScriptPath.setText(AppConfig.INSTANCE.getScriptPath());
    }

    private void setupListeners() {
        etPipMirror.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                AppConfig.INSTANCE.setPipMirror(s.toString());
            }
        });

        swLogShowInternal.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppConfig.INSTANCE.setLogShowInternal(isChecked));

        etScriptPath.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                AppConfig.INSTANCE.setScriptPath(s.toString());
            }
        });

        findViewById(R.id.about_developer).setOnClickListener(v -> {
            Intent intent = new Intent(this, InfoPageActivity.class);
            intent.putExtra(InfoPageActivity.EXTRA_TITLE, getString(R.string.developer_info_title));
            intent.putExtra(InfoPageActivity.EXTRA_CONTENT, getString(R.string.developer_info_content));
            startActivity(intent);
        });
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
